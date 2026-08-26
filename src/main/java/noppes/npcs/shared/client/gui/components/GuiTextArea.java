package noppes.npcs.shared.client.gui.components;

import noppes.npcs.client.CustomNpcResourceListener;

import net.minecraft.client.gui.*;
import noppes.npcs.shared.client.gui.listeners.*;
import com.mojang.blaze3d.matrix.*;
import noppes.npcs.mixin.*;
import net.minecraft.client.*;
import java.util.regex.*;
import net.minecraft.util.*;
import net.minecraft.client.gui.screen.*;
import net.minecraft.client.util.*;
import noppes.npcs.shared.client.util.*;
import java.util.ArrayList;
import java.util.List;
import noppes.npcs.*;
import java.awt.Font;

public class GuiTextArea extends AbstractGui implements IGui, IGuiEventListener
{
    public int id;
    public int x;
    public int y;
    public int width;
    public int height;
    private int cursorCounter;
    private ITextChangeListener listener;
    private static TrueTypeFont font;
    public String text;
    private TextContainer container;
    public boolean active;
    public boolean enabled;
    public boolean visible;
    public boolean clicked;
    public boolean doubleClicked;
    public boolean clickScrolling;
    private int startSelection;
    private int endSelection;
    private int cursorPosition;
    private int scrolledLine;
    private boolean enableCodeHighlighting;
    private static final char colorChar = '\uffff';
    public List<UndoData> undoList;
    public List<UndoData> redoList;
    public boolean undoing;
    private long lastClicked;
    
    public GuiTextArea(final int id, final int x, final int y, final int width, final int height, final String text) {
        this.text = null;
        this.container = null;
        this.active = false;
        this.enabled = true;
        this.visible = true;
        this.clicked = false;
        this.doubleClicked = false;
        this.clickScrolling = false;
        this.scrolledLine = 0;
        this.enableCodeHighlighting = false;
        this.undoList = new ArrayList<UndoData>();
        this.redoList = new ArrayList<UndoData>();
        this.undoing = false;
        this.lastClicked = 0L;
        this.id = id;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.undoing = true;
        this.setText(text);
        this.undoing = false;
        GuiTextArea.font.setSpecial('\uffff');
    }
    
    public void render(final MatrixStack matrixStack, final int xMouse, final int yMouse) {
        if (!this.visible) {
            return;
        }
        this.clampSelection();
        fill(matrixStack, this.x - 1, this.y - 1, this.x + this.width + 1, this.y + this.height + 1, -6250336);
        fill(matrixStack, this.x, this.y, this.x + this.width, this.y + this.height, -16777216);
        this.container.visibleLines = this.height / this.container.lineHeight;
        if (this.clicked) {
            this.clicked = (((MouseHelperMixin)Minecraft.getInstance().mouseHandler).getActiveButton() == 0);
            final int i = this.getSelectionPos(xMouse, yMouse);
            if (i != this.cursorPosition) {
                if (this.doubleClicked) {
                    final int cursorPosition = this.cursorPosition;
                    this.endSelection = cursorPosition;
                    this.startSelection = cursorPosition;
                    this.doubleClicked = false;
                }
                this.setCursor(i, true);
            }
        }
        else if (this.doubleClicked) {
            this.doubleClicked = false;
        }
        if (this.clickScrolling) {
            this.clickScrolling = (((MouseHelperMixin)Minecraft.getInstance().mouseHandler).getActiveButton() == 0);
            final int diff = this.container.linesCount - this.container.visibleLines;
            this.scrolledLine = Math.min(Math.max((int)(1.0f * diff * (yMouse - this.y) / this.height), 0), diff);
        }
        int startBracket = 0;
        int endBracket = 0;
        if (this.endSelection - this.startSelection == 1 || (this.startSelection == this.endSelection && this.startSelection < this.text.length())) {
            final char c = this.text.charAt(this.startSelection);
            int found = 0;
            if (c == '{') {
                found = this.findClosingBracket(this.text.substring(this.startSelection), '{', '}');
            }
            else if (c == '[') {
                found = this.findClosingBracket(this.text.substring(this.startSelection), '[', ']');
            }
            else if (c == '(') {
                found = this.findClosingBracket(this.text.substring(this.startSelection), '(', ')');
            }
            else if (c == '}') {
                found = this.findOpeningBracket(this.text.substring(0, this.startSelection + 1), '{', '}');
            }
            else if (c == ']') {
                found = this.findOpeningBracket(this.text.substring(0, this.startSelection + 1), '[', ']');
            }
            else if (c == ')') {
                found = this.findOpeningBracket(this.text.substring(0, this.startSelection + 1), '(', ')');
            }
            if (found != 0) {
                startBracket = this.startSelection;
                endBracket = this.startSelection + found;
            }
        }
        final List<TextContainer.LineData> list = new ArrayList<TextContainer.LineData>(this.container.lines);
        String wordHightLight = null;
        if (this.startSelection != this.endSelection) {
            final Matcher m = this.container.regexWord.matcher(this.text);
            while (m.find()) {
                if (m.start() == this.startSelection && m.end() == this.endSelection) {
                    wordHightLight = this.text.substring(this.startSelection, this.endSelection);
                }
            }
        }
        for (int j = 0; j < list.size(); ++j) {
            final TextContainer.LineData data = list.get(j);
            final String line = data.text;
            final int w = line.length();
            if (startBracket != endBracket) {
                if (startBracket >= data.start && startBracket < data.end) {
                    final int s = GuiTextArea.font.width(line.substring(0, startBracket - data.start));
                    final int e = GuiTextArea.font.width(line.substring(0, startBracket - data.start + 1)) + 1;
                    final int posY = this.y + 1 + (j - this.scrolledLine) * this.container.lineHeight;
                    fill(matrixStack, this.x + 1 + s, posY, this.x + 1 + e, posY + this.container.lineHeight + 1, -1728001024);
                }
                if (endBracket >= data.start && endBracket < data.end) {
                    final int s = GuiTextArea.font.width(line.substring(0, endBracket - data.start));
                    final int e = GuiTextArea.font.width(line.substring(0, endBracket - data.start + 1)) + 1;
                    final int posY = this.y + 1 + (j - this.scrolledLine) * this.container.lineHeight;
                    fill(matrixStack, this.x + 1 + s, posY, this.x + 1 + e, posY + this.container.lineHeight + 1, -1728001024);
                }
            }
            if (j >= this.scrolledLine && j < this.scrolledLine + this.container.visibleLines) {
                if (wordHightLight != null) {
                    final Matcher k = this.container.regexWord.matcher(line);
                    while (k.find()) {
                        if (line.substring(k.start(), k.end()).equals(wordHightLight)) {
                            final int s2 = GuiTextArea.font.width(line.substring(0, k.start()));
                            final int e2 = GuiTextArea.font.width(line.substring(0, k.end())) + 1;
                            final int posY2 = this.y + 1 + (j - this.scrolledLine) * this.container.lineHeight;
                            fill(matrixStack, this.x + 1 + s2, posY2, this.x + 1 + e2, posY2 + this.container.lineHeight + 1, -1728033792);
                        }
                    }
                }
                if (this.startSelection != this.endSelection && this.endSelection > data.start && this.startSelection <= data.end && this.startSelection < data.end) {
                    final int s = GuiTextArea.font.width(line.substring(0, Math.max(this.startSelection - data.start, 0)));
                    final int e = GuiTextArea.font.width(line.substring(0, Math.min(this.endSelection - data.start, w))) + 1;
                    final int posY = this.y + 1 + (j - this.scrolledLine) * this.container.lineHeight;
                    fill(matrixStack, this.x + 1 + s, posY, this.x + 1 + e, posY + this.container.lineHeight + 1, -1728052993);
                }
                final int yPos = this.y + (j - this.scrolledLine) * this.container.lineHeight + 1;
                GuiTextArea.font.draw(data.getFormattedString(), (float)(this.x + 1), (float)yPos, CustomNpcResourceListener.DefaultTextColor);
                if (this.active && this.isEnabled() && this.cursorCounter / 6 % 2 == 0 && this.cursorPosition >= data.start && this.cursorPosition < data.end) {
                    final int posX = this.x + GuiTextArea.font.width(line.substring(0, this.cursorPosition - data.start));
                    fill(matrixStack, posX + 1, yPos, posX + 2, yPos + 1 + this.container.lineHeight, -3092272);
                }
            }
        }
        if (this.hasVerticalScrollbar()) {
            Minecraft.getInstance().getTextureManager().bind(GuiCustomScroll.resource);
            final int sbSize = Math.max((int)(1.0f * this.container.visibleLines / this.container.linesCount * this.height), 2);
            final int posX2 = this.x + this.width - 6;
            final int posY3 = (int)(this.y + 1.0f * this.scrolledLine / this.container.linesCount * (this.height - 4)) + 1;
            fill(matrixStack, posX2, posY3, posX2 + 5, posY3 + sbSize, -2039584);
        }
    }
    
    private int findClosingBracket(final String str, final char s, final char e) {
        int found = 0;
        final char[] chars = str.toCharArray();
        for (int i = 0; i < chars.length; ++i) {
            final char c = chars[i];
            if (c == s) {
                ++found;
            }
            else if (c == e && --found == 0) {
                return i;
            }
        }
        return 0;
    }
    
    private int findOpeningBracket(final String str, final char s, final char e) {
        int found = 0;
        final char[] chars = str.toCharArray();
        for (int i = chars.length - 1; i >= 0; --i) {
            final char c = chars[i];
            if (c == e) {
                ++found;
            }
            else if (c == s && --found == 0) {
                return i - chars.length + 1;
            }
        }
        return 0;
    }
    
    private int getSelectionPos(double xMouse, double yMouse) {
        xMouse -= this.x + 1;
        yMouse -= this.y + 1;
        final List<TextContainer.LineData> list = new ArrayList<TextContainer.LineData>(this.container.lines);
        for (int i = 0; i < list.size(); ++i) {
            final TextContainer.LineData data = list.get(i);
            if (i >= this.scrolledLine && i < this.scrolledLine + this.container.visibleLines) {
                final int yPos = (i - this.scrolledLine) * this.container.lineHeight;
                if (yMouse >= yPos && yMouse < yPos + this.container.lineHeight) {
                    int lineWidth = 0;
                    final char[] chars = data.text.toCharArray();
                    for (int j = 1; j <= chars.length; ++j) {
                        final int w = GuiTextArea.font.width(data.text.substring(0, j));
                        if (xMouse < lineWidth + (w - lineWidth) / 2) {
                            return data.start + j - 1;
                        }
                        lineWidth = w;
                    }
                    return data.end - 1;
                }
            }
        }
        return this.container.text.length();
    }
    
    public int getID() {
        return this.id;
    }
    
    public boolean charTyped(final char c, final int i) {
        if (!this.active) {
            return false;
        }
        if (!this.isEnabled()) {
            return false;
        }
        if (SharedConstants.isAllowedChatCharacter(c)) {
            this.addText(Character.toString(c));
        }
        return true;
    }
    
    public boolean keyPressed(final int keyCode, final int scanCode, final int modifiers) {
        if (!this.active) {
            return false;
        }
        this.clampSelection();
        if (Screen.isSelectAll(keyCode)) {
            final int n = 0;
            this.cursorPosition = n;
            this.startSelection = n;
            this.endSelection = this.text.length();
            return true;
        }
        if (keyCode == 263) {
            int j = 1;
            if (Screen.hasControlDown()) {
                final Matcher m = this.container.regexWord.matcher(this.text.substring(0, this.cursorPosition));
                while (m.find()) {
                    if (m.start() == m.end()) {
                        continue;
                    }
                    j = this.cursorPosition - m.start();
                }
            }
            this.setCursor(this.cursorPosition - j, Screen.hasShiftDown());
            return true;
        }
        if (keyCode == 262) {
            int j = 1;
            if (Screen.hasControlDown()) {
                final Matcher m = this.container.regexWord.matcher(this.text.substring(this.cursorPosition));
                if ((m.find() && m.start() > 0) || m.find()) {
                    j = m.start();
                }
            }
            this.setCursor(this.cursorPosition + j, Screen.hasShiftDown());
            return true;
        }
        if (keyCode == InputMappings.getKey("key.keyboard.up").getValue()) {
            this.setCursor(this.cursorUp(), Screen.hasShiftDown());
            return true;
        }
        if (keyCode == InputMappings.getKey("key.keyboard.down").getValue()) {
            this.setCursor(this.cursorDown(), Screen.hasShiftDown());
            return true;
        }
        if (keyCode == 261) {
            String s = this.getSelectionAfterText();
            if (!s.isEmpty() && this.startSelection == this.endSelection) {
                s = s.substring(1);
            }
            this.setText(this.getSelectionBeforeText() + s);
            final int startSelection = this.startSelection;
            this.cursorPosition = startSelection;
            this.endSelection = startSelection;
            return true;
        }
        if (keyCode == 259) {
            String s = this.getSelectionBeforeText();
            if (this.startSelection > 0 && this.startSelection == this.endSelection) {
                s = s.substring(0, s.length() - 1);
                --this.startSelection;
            }
            this.setText(s + this.getSelectionAfterText());
            final int startSelection2 = this.startSelection;
            this.cursorPosition = startSelection2;
            this.endSelection = startSelection2;
            return true;
        }
        if (Screen.isCut(keyCode)) {
            this.clampSelection();
            if (this.startSelection != this.endSelection) {
                NoppesStringUtils.setClipboardContents(this.text.substring(this.startSelection, this.endSelection));
                final String s = this.getSelectionBeforeText();
                this.setText(s + this.getSelectionAfterText());
                final int length = s.length();
                this.cursorPosition = length;
                this.startSelection = length;
                this.endSelection = length;
            }
            return true;
        }
        if (Screen.isCopy(keyCode)) {
            this.clampSelection();
            if (this.startSelection != this.endSelection) {
                NoppesStringUtils.setClipboardContents(this.text.substring(this.startSelection, this.endSelection));
            }
            return true;
        }
        if (Screen.isPaste(keyCode)) {
            this.addText(NoppesStringUtils.getClipboardContents());
            return true;
        }
        if (keyCode == 90 && Screen.hasControlDown()) {
            if (this.undoList.isEmpty()) {
                return true;
            }
            this.undoing = true;
            this.redoList.add(new UndoData(this.text, this.cursorPosition));
            final UndoData data = this.undoList.remove(this.undoList.size() - 1);
            this.setText(data.text);
            final int cursorPosition = data.cursorPosition;
            this.cursorPosition = cursorPosition;
            this.startSelection = cursorPosition;
            this.endSelection = cursorPosition;
            this.undoing = false;
            return true;
        }
        else {
            if (keyCode != 89 || !Screen.hasControlDown()) {
                if (keyCode == 258) {
                    this.addText("    ");
                }
                if (keyCode == 257 || keyCode == 335) {
                    this.addText(Character.toString('\n') + this.getIndentCurrentLine());
                }
                return true;
            }
            if (this.redoList.isEmpty()) {
                return true;
            }
            this.undoing = true;
            this.undoList.add(new UndoData(this.text, this.cursorPosition));
            final UndoData data = this.redoList.remove(this.redoList.size() - 1);
            this.setText(data.text);
            final int cursorPosition2 = data.cursorPosition;
            this.cursorPosition = cursorPosition2;
            this.startSelection = cursorPosition2;
            this.endSelection = cursorPosition2;
            this.undoing = false;
            return true;
        }
    }
    
    private String getIndentCurrentLine() {
        for (final TextContainer.LineData data : this.container.lines) {
            if (this.cursorPosition > data.start && this.cursorPosition <= data.end) {
                int i;
                for (i = 0; i < data.text.length() && data.text.charAt(i) == ' '; ++i) {}
                return data.text.substring(0, i);
            }
        }
        return "";
    }
    
    private void setCursor(int i, final boolean select) {
        i = Math.min(Math.max(i, 0), this.text.length());
        if (i == this.cursorPosition) {
            return;
        }
        if (!select) {
            final int endSelection = i;
            this.cursorPosition = endSelection;
            this.startSelection = endSelection;
            this.endSelection = endSelection;
            return;
        }
        final int diff = this.cursorPosition - i;
        if (this.cursorPosition == this.startSelection) {
            this.startSelection -= diff;
        }
        else if (this.cursorPosition == this.endSelection) {
            this.endSelection -= diff;
        }
        if (this.startSelection > this.endSelection) {
            final int j = this.endSelection;
            this.endSelection = this.startSelection;
            this.startSelection = j;
        }
        this.cursorPosition = i;
    }
    
    public void addText(final String s) {
        this.clampSelection();
        // setText strips \r; use the same length so cursor/selection stay in sync
        final String insert = s.replace("\r", "");
        this.setText(this.getSelectionBeforeText() + insert + this.getSelectionAfterText());
        final int endSelection = this.startSelection + insert.length();
        this.cursorPosition = endSelection;
        this.startSelection = endSelection;
        this.endSelection = endSelection;
    }

    /** Keep cursor/selection indices within [0, text.length()]. */
    private void clampSelection() {
        final int len = (this.text == null) ? 0 : this.text.length();
        this.startSelection = Math.max(0, Math.min(this.startSelection, len));
        this.endSelection = Math.max(0, Math.min(this.endSelection, len));
        if (this.startSelection > this.endSelection) {
            final int t = this.startSelection;
            this.startSelection = this.endSelection;
            this.endSelection = t;
        }
        this.cursorPosition = Math.max(0, Math.min(this.cursorPosition, len));
    }
    
    private int cursorUp() {
        int i = 0;
        while (i < this.container.lines.size()) {
            final TextContainer.LineData data = this.container.lines.get(i);
            if (this.cursorPosition >= data.start && this.cursorPosition < data.end) {
                if (i == 0) {
                    return 0;
                }
                final int linePos = this.cursorPosition - data.start;
                return this.getSelectionPos(this.x + 1 + GuiTextArea.font.width(data.text.substring(0, this.cursorPosition - data.start)), this.y + 1 + (i - 1 - this.scrolledLine) * this.container.lineHeight);
            }
            else {
                ++i;
            }
        }
        return 0;
    }
    
    private int cursorDown() {
        for (int i = 0; i < this.container.lines.size(); ++i) {
            final TextContainer.LineData data = this.container.lines.get(i);
            if (this.cursorPosition >= data.start && this.cursorPosition < data.end) {
                final int linePos = this.cursorPosition - data.start;
                return this.getSelectionPos(this.x + 1 + GuiTextArea.font.width(data.text.substring(0, this.cursorPosition - data.start)), this.y + 1 + (i + 1 - this.scrolledLine) * this.container.lineHeight);
            }
        }
        return this.text.length();
    }
    
    public String getSelectionBeforeText() {
        this.clampSelection();
        if (this.startSelection == 0) {
            return "";
        }
        return this.text.substring(0, this.startSelection);
    }
    
    public String getSelectionAfterText() {
        this.clampSelection();
        return this.text.substring(this.endSelection);
    }
    
    public boolean mouseClicked(final double xMouse, final double yMouse, final int mouseButton) {
        this.active = (xMouse >= this.x && xMouse < this.x + this.width && yMouse >= this.y && yMouse < this.y + this.height);
        if (this.active) {
            final int selectionPos = this.getSelectionPos(xMouse, yMouse);
            this.cursorPosition = selectionPos;
            this.endSelection = selectionPos;
            this.startSelection = selectionPos;
            this.clicked = (mouseButton == 0);
            this.doubleClicked = false;
            final long time = System.currentTimeMillis();
            if (this.clicked && this.container.linesCount * this.container.lineHeight > this.height && xMouse > this.x + this.width - 8) {
                this.clicked = false;
                this.clickScrolling = true;
            }
            else if (time - this.lastClicked < 500L) {
                this.doubleClicked = true;
                final Matcher m = this.container.regexWord.matcher(this.text);
                while (m.find()) {
                    if (this.cursorPosition > m.start() && this.cursorPosition < m.end()) {
                        this.startSelection = m.start();
                        this.endSelection = m.end();
                        break;
                    }
                }
            }
            this.lastClicked = time;
        }
        return this.active;
    }
    
    public void tick() {
        ++this.cursorCounter;
    }
    
    public boolean mouseScrolled(final double mouseX, final double mouseY, final double scrolled) {
        if (scrolled != 0.0) {
            this.scrolledLine += ((scrolled > 0.0) ? -1 : 1);
            this.scrolledLine = Math.max(Math.min(this.scrolledLine, this.container.linesCount - this.height / this.container.lineHeight), 0);
        }
        return true;
    }
    
    public void setText(String text) {
        text = text.replace("\r", "");
        if (this.text != null && this.text.equals(text)) {
            return;
        }
        if (this.listener != null) {
            this.listener.textUpdate(text);
        }
        if (!this.undoing) {
            this.undoList.add(new UndoData(this.text, this.cursorPosition));
            this.redoList.clear();
        }
        this.text = text;
        this.clampSelection();
        (this.container = new TextContainer(text)).init(GuiTextArea.font, this.width, this.height);
        if (this.enableCodeHighlighting) {
            this.container.formatCodeText();
        }
        if (this.scrolledLine > this.container.linesCount - this.container.visibleLines) {
            this.scrolledLine = Math.max(0, this.container.linesCount - this.container.visibleLines);
        }
    }
    
    public String getText() {
        return this.text;
    }
    
    public boolean isEnabled() {
        return this.enabled && this.visible;
    }
    
    public boolean hasVerticalScrollbar() {
        return this.container.visibleLines < this.container.linesCount;
    }
    
    public void enableCodeHighlighting() {
        this.enableCodeHighlighting = true;
        this.container.formatCodeText();
    }
    
    public void setListener(final ITextChangeListener listener) {
        this.listener = listener;
    }
    
    public boolean isActive() {
        return this.active;
    }
    
    static {
        GuiTextArea.font = new TrueTypeFont(new Font("Arial Unicode MS", 0, CustomNpcs.FontSize), 1.0f);
    }
    
    class UndoData
    {
        public String text;
        public int cursorPosition;
        
        public UndoData(final String text, final int cursorPosition) {
            this.text = text;
            this.cursorPosition = cursorPosition;
        }
    }
}
