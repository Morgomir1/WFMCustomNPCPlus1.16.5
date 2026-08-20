package noppes.npcs.client;

import noppes.npcs.*;
import net.minecraft.command.*;
import noppes.npcs.shared.client.util.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.*;
import net.minecraft.util.text.*;

public class TextBlockClient extends TextBlock
{
    public int color;
    private String name;
    private CommandSource sender;
    
    public TextBlockClient(final String name, final String text, final int lineWidth, final int color, final Object... obs) {
        this(text, lineWidth, false, obs);
        this.color = color;
        this.name = name;
    }
    
    public TextBlockClient(final CommandSource sender, final String text, final int lineWidth, final int color, final Object... obs) {
        this(text, lineWidth, false, obs);
        this.color = color;
        this.sender = sender;
    }
    
    public String getName() {
        if (this.sender != null) {
            return this.sender.getTextName();
        }
        return this.name;
    }
    
    public TextBlockClient(String text, final int lineWidth, final boolean mcFont, final Object... obs) {
        this.color = 14737632;
        text = NoppesStringUtils.formatText(text, obs);
        String line = "";
        text = text.replace("\n", " \n ");
        text = text.replace("\r", " \r ");
        final String[] words = text.split(" ");
        final FontRenderer font = Minecraft.getInstance().font;
        for (final String word : words) {
            Label_0224: {
                if (!word.isEmpty()) {
                    if (word.length() == 1) {
                        final char c = word.charAt(0);
                        if (c == '\r' || c == '\n') {
                            this.addLine(line);
                            line = trailingFormat(line);
                            break Label_0224;
                        }
                    }
                    String newLine;
                    if (line.isEmpty()) {
                        newLine = word;
                    }
                    else {
                        newLine = line + " " + word;
                    }
                    if ((mcFont ? font.width(newLine) : ClientProxy.Font.width(newLine)) > lineWidth) {
                        this.addLine(line);
                        line = trailingFormat(line) + word.trim();
                    }
                    else {
                        line = newLine;
                    }
                }
            }
        }
        if (!line.isEmpty()) {
            this.addLine(line);
        }
    }
    
    private void addLine(final String text) {
        final StringTextComponent line = new StringTextComponent(text);
        this.lines.add((ITextComponent)line);
    }
    
    private static String trailingFormat(final String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        final char section = (char)167;
        String color = "";
        final StringBuilder styles = new StringBuilder();
        for (int i = 0; i < text.length() - 1; ++i) {
            if (text.charAt(i) == section) {
                final char code = Character.toLowerCase(text.charAt(i + 1));
                final int index = "0123456789abcdefklmnor".indexOf(code);
                if (index >= 0) {
                    if (index < 16) {
                        color = section + String.valueOf(code);
                        styles.setLength(0);
                    }
                    else if (code == 'r') {
                        color = "";
                        styles.setLength(0);
                    }
                    else {
                        styles.append(section).append(code);
                    }
                    ++i;
                }
            }
        }
        return color + styles.toString();
    }
}
