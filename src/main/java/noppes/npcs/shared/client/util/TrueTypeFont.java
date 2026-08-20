package noppes.npcs.shared.client.util;

import noppes.npcs.shared.common.util.*;
import java.awt.image.*;
import net.minecraft.util.*;
import net.minecraft.client.*;
import net.minecraft.resources.*;
import java.io.*;
import com.mojang.blaze3d.systems.*;
import net.minecraft.util.text.*;
import noppes.npcs.client.renderer.*;
import java.awt.*;
import net.minecraft.client.renderer.vertex.*;
import net.minecraft.client.renderer.*;
import java.util.*;
import java.util.List;

import org.lwjgl.opengl.*;

public class TrueTypeFont
{
    private static final int MaxWidth = 512;
    private static final List<Font> allFonts;
    private List<Font> usedFonts;
    private LinkedHashMap<String, GlyphCache> textcache;
    private Map<Character, Glyph> glyphcache;
    private List<TextureCache> textures;
    private Font font;
    private int lineHeight;
    private Graphics2D globalG;
    public float scale;
    private int specialChar;
    
    public TrueTypeFont(final Font font, final float scale) {
        this.usedFonts = new ArrayList<Font>();
        this.textcache = new LRUHashMap<String, GlyphCache>(100);
        this.glyphcache = new HashMap<Character, Glyph>();
        this.textures = new ArrayList<TextureCache>();
        this.lineHeight = 1;
        this.globalG = (Graphics2D)new BufferedImage(1, 1, 2).getGraphics();
        this.scale = 1.0f;
        this.specialChar = 167;
        this.font = font;
        this.scale = scale;
        this.globalG.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        this.lineHeight = this.globalG.getFontMetrics(font).getHeight();
    }
    
    public TrueTypeFont(final ResourceLocation resource, final int fontSize, final float scale) throws IOException, FontFormatException {
        this.usedFonts = new ArrayList<Font>();
        this.textcache = new LRUHashMap<String, GlyphCache>(100);
        this.glyphcache = new HashMap<Character, Glyph>();
        this.textures = new ArrayList<TextureCache>();
        this.lineHeight = 1;
        this.globalG = (Graphics2D)new BufferedImage(1, 1, 2).getGraphics();
        this.scale = 1.0f;
        this.specialChar = 167;
        try (final IResource r = Minecraft.getInstance().getResourceManager().getResource(resource)) {
            final InputStream stream = r.getInputStream();
            final GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
            final Font font = Font.createFont(0, stream);
            ge.registerFont(font);
            this.font = font.deriveFont(0, (float)fontSize);
            this.scale = scale;
            this.globalG.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            this.lineHeight = this.globalG.getFontMetrics(font).getHeight();
        }
        catch (IOException e) {
            throw e;
        }
    }
    
    public void setSpecial(final char c) {
        this.specialChar = c;
    }
    
    public void draw(final String text, final float x, final float y, final int color) {
        final GlyphCache cache = this.getOrCreateCache(text);
        float cr = (color >> 16 & 0xFF) / 255.0f;
        float cg = (color >> 8 & 0xFF) / 255.0f;
        float cb = (color & 0xFF) / 255.0f;
        RenderSystem.color4f(cr, cg, cb, 1.0f);
        RenderSystem.enableBlend();
        RenderSystem.pushMatrix();
        RenderSystem.translatef(x, y, 0.0f);
        RenderSystem.scalef(this.scale, this.scale, 1.0f);
        float i = 0.0f;
        boolean bold = false;
        boolean italic = false;
        boolean underline = false;
        boolean strikethrough = false;
        for (final Glyph gl : cache.glyphs) {
            if (gl.type != GlyphType.NORMAL) {
                if (gl.type == GlyphType.COLOR) {
                    cr = (gl.color >> 16 & 0xFF) / 255.0f;
                    cg = (gl.color >> 8 & 0xFF) / 255.0f;
                    cb = (gl.color & 0xFF) / 255.0f;
                    RenderSystem.color4f(cr, cg, cb, 1.0f);
                    bold = false;
                    italic = false;
                    underline = false;
                    strikethrough = false;
                }
                else if (gl.type == GlyphType.RESET) {
                    cr = (color >> 16 & 0xFF) / 255.0f;
                    cg = (color >> 8 & 0xFF) / 255.0f;
                    cb = (color & 0xFF) / 255.0f;
                    RenderSystem.color4f(cr, cg, cb, 1.0f);
                    bold = false;
                    italic = false;
                    underline = false;
                    strikethrough = false;
                }
                else if (gl.type == GlyphType.BOLD) {
                    bold = true;
                }
                else if (gl.type == GlyphType.ITALIC) {
                    italic = true;
                }
                else if (gl.type == GlyphType.UNDERLINE) {
                    underline = true;
                }
                else if (gl.type == GlyphType.STRIKETHROUGH) {
                    strikethrough = true;
                }
                continue;
            }
            final float gw = gl.width * this.textureScale();
            final float gh = gl.height * this.textureScale();
            RenderSystem.bindTexture(gl.texture);
            this.fillGradient(i, 0.0f, gl.x * this.textureScale(), gl.y * this.textureScale(), gw, gh, italic);
            float advance = gw;
            if (bold) {
                this.fillGradient(i + 1.0f, 0.0f, gl.x * this.textureScale(), gl.y * this.textureScale(), gw, gh, italic);
                advance += 1.0f;
            }
            if (underline || strikethrough) {
                if (underline) {
                    this.drawFormatLine(i, gh - 1.0f, advance, cr, cg, cb);
                }
                if (strikethrough) {
                    this.drawFormatLine(i, gh * 0.5f, advance, cr, cg, cb);
                }
                RenderSystem.color4f(cr, cg, cb, 1.0f);
            }
            i += advance;
        }
        RenderSystem.disableBlend();
        RenderSystem.popMatrix();
        RenderSystem.color4f(1.0f, 1.0f, 1.0f, 1.0f);
    }
    
    private GlyphCache getOrCreateCache(final String text) {
        GlyphCache cache = this.textcache.get(text);
        if (cache != null) {
            return cache;
        }
        cache = new GlyphCache();
        for (int i = 0; i < text.length(); ++i) {
            final char c = text.charAt(i);
            if (c == this.specialChar && i + 1 < text.length()) {
                final char next = text.toLowerCase(Locale.ENGLISH).charAt(i + 1);
                final int index = "0123456789abcdefklmnor".indexOf(next);
                if (index >= 0) {
                    final Glyph g = new Glyph();
                    if (index < 16) {
                        g.type = GlyphType.COLOR;
                        g.color = TextFormatting.getByCode(next).getColor();
                    }
                    else if (index == 16) {
                        g.type = GlyphType.RANDOM;
                    }
                    else if (index == 17) {
                        g.type = GlyphType.BOLD;
                    }
                    else if (index == 18) {
                        g.type = GlyphType.STRIKETHROUGH;
                    }
                    else if (index == 19) {
                        g.type = GlyphType.UNDERLINE;
                    }
                    else if (index == 20) {
                        g.type = GlyphType.ITALIC;
                    }
                    else {
                        g.type = GlyphType.RESET;
                    }
                    cache.glyphs.add(g);
                    ++i;
                    continue;
                }
            }
            final Glyph g2 = this.getOrCreateGlyph(c);
            cache.glyphs.add(g2);
            final GlyphCache glyphCache = cache;
            glyphCache.width += g2.width;
            cache.height = Math.max(cache.height, g2.height);
        }
        this.textcache.put(text, cache);
        return cache;
    }
    
    private Glyph getOrCreateGlyph(final char c) {
        Glyph g = this.glyphcache.get(c);
        if (g != null) {
            return g;
        }
        TextureCache cache = this.getCurrentTexture();
        final Font font = this.getFontForChar(c);
        final FontMetrics metrics = this.globalG.getFontMetrics(font);
        g = new Glyph();
        g.width = Math.max(metrics.charWidth(c), 1);
        g.height = Math.max(metrics.getHeight(), 1);
        if (cache.x + g.width >= 512) {
            cache.x = 0;
            final TextureCache textureCache = cache;
            textureCache.y += this.lineHeight + 1;
            if (cache.y >= 512) {
                cache.full = true;
                cache = this.getCurrentTexture();
            }
        }
        g.x = cache.x;
        g.y = cache.y;
        final TextureCache textureCache2 = cache;
        textureCache2.x += g.width + 3;
        this.lineHeight = Math.max(this.lineHeight, g.height);
        cache.g.setFont(font);
        cache.g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        cache.g.drawString(c + "", g.x, g.y + metrics.getAscent());
        g.texture = cache.textureId;
        CTextureUtil.uploadTextureImage(cache.textureId, cache.bufferedImage);
        this.glyphcache.put(c, g);
        return g;
    }
    
    private TextureCache getCurrentTexture() {
        TextureCache cache = null;
        for (final TextureCache t : this.textures) {
            if (!t.full) {
                cache = t;
                break;
            }
        }
        if (cache == null) {
            this.textures.add(cache = new TextureCache());
        }
        return cache;
    }
    
    public void drawCentered(final String text, final float x, final float y, final int color) {
        this.draw(text, x - this.width(text) / 2.0f, y, color);
    }
    
    private Font getFontForChar(final char c) {
        if (this.font.canDisplay(c)) {
            return this.font;
        }
        for (final Font f : this.usedFonts) {
            if (f.canDisplay(c)) {
                return f;
            }
        }
        final Font fa = new Font("Arial Unicode MS", 0, this.font.getSize());
        if (fa.canDisplay(c)) {
            return fa;
        }
        for (Font f2 : TrueTypeFont.allFonts) {
            if (f2.canDisplay(c)) {
                this.usedFonts.add(f2 = f2.deriveFont(0, (float)this.font.getSize()));
                return f2;
            }
        }
        return this.font;
    }
    
    public void fillGradient(final float x, final float y, final float textureX, final float textureY, final float width, final float height) {
        this.fillGradient(x, y, textureX, textureY, width, height, false);
    }
    
    public void fillGradient(final float x, final float y, final float textureX, final float textureY, final float width, final float height, final boolean italic) {
        final float f = 0.00390625f;
        final float f2 = 0.00390625f;
        final int zLevel = 0;
        final float italicOffset = italic ? 1.0f : 0.0f;
        final BufferBuilder tessellator = Tessellator.getInstance().getBuilder();
        tessellator.begin(7, DefaultVertexFormats.POSITION_TEX);
        tessellator.vertex((double)(x - italicOffset), (double)(y + height), (double)zLevel).uv(textureX * f, (textureY + height) * f2).endVertex();
        tessellator.vertex((double)(x + width - italicOffset), (double)(y + height), (double)zLevel).uv((textureX + width) * f, (textureY + height) * f2).endVertex();
        tessellator.vertex((double)(x + width + italicOffset), (double)y, (double)zLevel).uv((textureX + width) * f, textureY * f2).endVertex();
        tessellator.vertex((double)(x + italicOffset), (double)y, (double)zLevel).uv(textureX * f, textureY * f2).endVertex();
        Tessellator.getInstance().end();
    }
    
    private void drawFormatLine(final float x, final float y, final float width, final float r, final float g, final float b) {
        RenderSystem.disableTexture();
        final BufferBuilder tessellator = Tessellator.getInstance().getBuilder();
        tessellator.begin(7, DefaultVertexFormats.POSITION_COLOR);
        tessellator.vertex((double)x, (double)(y + 1.0f), 0.0).color(r, g, b, 1.0f).endVertex();
        tessellator.vertex((double)(x + width), (double)(y + 1.0f), 0.0).color(r, g, b, 1.0f).endVertex();
        tessellator.vertex((double)(x + width), (double)y, 0.0).color(r, g, b, 1.0f).endVertex();
        tessellator.vertex((double)x, (double)y, 0.0).color(r, g, b, 1.0f).endVertex();
        Tessellator.getInstance().end();
        RenderSystem.enableTexture();
    }
    
    public int width(final String text) {
        final GlyphCache cache = this.getOrCreateCache(text);
        float w = 0.0f;
        boolean bold = false;
        for (final Glyph gl : cache.glyphs) {
            if (gl.type != GlyphType.NORMAL) {
                if (gl.type == GlyphType.COLOR || gl.type == GlyphType.RESET) {
                    bold = false;
                }
                else if (gl.type == GlyphType.BOLD) {
                    bold = true;
                }
                continue;
            }
            w += gl.width * this.textureScale();
            if (bold) {
                w += 1.0f;
            }
        }
        return (int)(w * this.scale);
    }
    
    public int height(final String text) {
        if (text == null || text.trim().isEmpty()) {
            return (int)(this.lineHeight * this.scale * this.textureScale());
        }
        final GlyphCache cache = this.getOrCreateCache(text);
        return Math.max(1, (int)(cache.height * this.scale * this.textureScale()));
    }
    
    private float textureScale() {
        return 0.5f;
    }
    
    public void dispose() {
        for (final TextureCache cache : this.textures) {
            RenderSystem.deleteTexture(cache.textureId);
        }
        this.textcache.clear();
    }
    
    public String getFontName() {
        return this.font.getFontName();
    }
    
    static {
        allFonts = Arrays.asList(GraphicsEnvironment.getLocalGraphicsEnvironment().getAllFonts());
    }
    
    enum GlyphType
    {
        NORMAL, 
        COLOR, 
        RANDOM, 
        BOLD, 
        STRIKETHROUGH, 
        UNDERLINE, 
        ITALIC, 
        RESET, 
        OTHER;
    }
    
    class TextureCache
    {
        int x;
        int y;
        int textureId;
        BufferedImage bufferedImage;
        Graphics2D g;
        boolean full;
        
        TextureCache() {
            this.textureId = GL11.glGenTextures();
            this.bufferedImage = new BufferedImage(512, 512, 2);
            this.g = (Graphics2D)this.bufferedImage.getGraphics();
        }
    }
    
    class Glyph
    {
        GlyphType type;
        int color;
        int x;
        int y;
        int height;
        int width;
        int texture;
        
        Glyph() {
            this.type = GlyphType.NORMAL;
            this.color = -1;
        }
    }
    
    class GlyphCache
    {
        public int width;
        public int height;
        List<Glyph> glyphs;
        
        GlyphCache() {
            this.glyphs = new ArrayList<Glyph>();
        }
    }
}
