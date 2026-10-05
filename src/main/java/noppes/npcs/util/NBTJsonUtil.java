package noppes.npcs.util;

import net.minecraft.nbt.*;
import noppes.npcs.mixin.*;
import java.util.*;
import org.apache.commons.io.Charsets;
import com.google.common.io.Files;
import java.io.*;

public class NBTJsonUtil
{
    public static String Convert(final CompoundNBT compound) {
        final List<JsonLine> list = new ArrayList<JsonLine>();
        final JsonLine line = ReadTag("", (INBT)compound, list);
        line.removeComma();
        return ConvertList(list);
    }

    public static CompoundNBT Convert(String json) throws JsonException {
        json = json.trim();
        final JsonFile file = new JsonFile(json);
        if (!json.startsWith("{") || !json.endsWith("}")) {
            throw new JsonException("Not properly incapsulated between { }", file);
        }
        final CompoundNBT compound = new CompoundNBT();
        FillCompound(compound, file);
        return compound;
    }

    public static void FillCompound(final CompoundNBT compound, final JsonFile json) throws JsonException {
        if (json.startsWith("{") || json.startsWith(",")) {
            json.cut(1);
        }
        if (json.startsWith("}")) {
            return;
        }
        final int index = json.keyIndex();
        if (index < 1) {
            throw new JsonException("Expected key after ,", json);
        }
        String key = json.substring(0, index);
        json.cut(index + 1);
        INBT base = ReadValue(json);
        if (base == null) {
            base = (INBT)StringNBT.valueOf("");
        }
        if (key.startsWith("\"")) {
            key = key.substring(1);
        }
        if (key.endsWith("\"")) {
            key = key.substring(0, key.length() - 1);
        }
        compound.put(key, base);
        if (json.startsWith(",")) {
            FillCompound(compound, json);
        }
    }

    public static INBT ReadValue(final JsonFile json) throws JsonException {
        if (json.startsWith("{")) {
            final CompoundNBT compound = new CompoundNBT();
            FillCompound(compound, json);
            if (!json.startsWith("}")) {
                throw new JsonException("Expected }", json);
            }
            json.cut(1);
            return (INBT)compound;
        }
        else if (json.startsWith("[")) {
            json.cut(1);
            final ListNBT list = new ListNBT();
            if (json.startsWith("B;") || json.startsWith("I;") || json.startsWith("L;")) {
                json.cut(2);
            }
            for (INBT value = ReadValue(json); value != null; value = ReadValue(json)) {
                list.add(value);
                if (!json.startsWith(",")) {
                    break;
                }
                json.cut(1);
            }
            if (!json.startsWith("]")) {
                throw new JsonException("Expected ]", json);
            }
            json.cut(1);
            if (list.getElementType() == 3) {
                final int[] arr = new int[list.size()];
                int i = 0;
                while (list.size() > 0) {
                    arr[i] = ((IntNBT)list.remove(0)).getAsInt();
                    ++i;
                }
                return (INBT)new IntArrayNBT(arr);
            }
            if (list.getElementType() == 1) {
                final byte[] arr2 = new byte[list.size()];
                int i = 0;
                while (list.size() > 0) {
                    arr2[i] = ((ByteNBT)list.remove(0)).getAsByte();
                    ++i;
                }
                return (INBT)new ByteArrayNBT(arr2);
            }
            if (list.getElementType() == 4) {
                final long[] arr3 = new long[list.size()];
                int i = 0;
                while (list.size() > 0) {
                    arr3[i] = ((LongNBT)list.remove(0)).getAsByte();
                    ++i;
                }
                return (INBT)new LongArrayNBT(arr3);
            }
            return (INBT)list;
        }
        else {
            if (json.startsWith("\"")) {
                json.cut(1);
                final StringBuilder s = new StringBuilder();
                String cut;
                for (boolean ignore = false; !json.startsWith("\"") || ignore; ignore = cut.equals("\\"), s.append(cut)) {
                    cut = json.cutDirty(1);
                }
                json.cut(1);
                return (INBT)StringNBT.valueOf(s.toString().replace("\\\\", "\\").replace("\\\"", "\""));
            }
            final StringBuilder literal = new StringBuilder();
            while (!json.startsWith(",", "]", "}")) {
                literal.append(json.cut(1));
            }
            final String s = literal.toString().trim().toLowerCase();
            if (s.isEmpty()) {
                return null;
            }
            try {
                if (s.endsWith("d")) {
                    return (INBT)DoubleNBT.valueOf(Double.parseDouble(s.substring(0, s.length() - 1)));
                }
                if (s.endsWith("f")) {
                    return (INBT)FloatNBT.valueOf(Float.parseFloat(s.substring(0, s.length() - 1)));
                }
                if (s.endsWith("b")) {
                    return (INBT)ByteNBT.valueOf(Byte.parseByte(s.substring(0, s.length() - 1)));
                }
                if (s.endsWith("s")) {
                    return (INBT)ShortNBT.valueOf(Short.parseShort(s.substring(0, s.length() - 1)));
                }
                if (s.endsWith("l")) {
                    return (INBT)LongNBT.valueOf(Long.parseLong(s.substring(0, s.length() - 1)));
                }
                if (s.contains(".")) {
                    return (INBT)DoubleNBT.valueOf(Double.parseDouble(s));
                }
                return (INBT)IntNBT.valueOf(Integer.parseInt(s));
            }
            catch (NumberFormatException ex) {
                throw new JsonException("Unable to convert: " + s + " to a number", json);
            }
        }
    }

    private static JsonLine ReadTag(String name, final INBT base, final List<JsonLine> list) {
        if (!name.isEmpty()) {
            name = "\"" + name + "\": ";
        }
        if (base.getId() == 9) {
            list.add(new JsonLine(name + "["));
            final ListNBT tags = (ListNBT)base;
            JsonLine line = null;
            final List<INBT> data = ((ListNBTMixin)tags).getList();
            for (final INBT b : data) {
                line = ReadTag("", b, list);
            }
            if (line != null) {
                line.removeComma();
            }
            list.add(new JsonLine("]"));
        }
        else if (base.getId() == 10) {
            list.add(new JsonLine(name + "{"));
            final CompoundNBT compound = (CompoundNBT)base;
            JsonLine line = null;
            for (final Object key : compound.getAllKeys()) {
                line = ReadTag(key.toString(), compound.get(key.toString()), list);
            }
            if (line != null) {
                line.removeComma();
            }
            list.add(new JsonLine("}"));
        }
        else if (base.getId() == 11) {
            list.add(new JsonLine(name + base.toString().replaceFirst(",]", "]")));
        }
        else if (base.getId() == 8) {
            list.add(new JsonLine(name + quoteAndEscape(base.getAsString())));
        }
        else {
            list.add(new JsonLine(name + base));
        }
        final JsonLine line2 = list.get(list.size() - 1);
        line2.line += ",";
        return line2;
    }

    private static String ConvertList(final List<JsonLine> list) {
        String json = "";
        int tab = 0;
        for (final JsonLine tag : list) {
            if (tag.reduceTab()) {
                --tab;
            }
            for (int i = 0; i < tab; ++i) {
                json += "    ";
            }
            json = json + tag + "\n";
            if (tag.increaseTab()) {
                ++tab;
            }
        }
        return json;
    }

    public static CompoundNBT LoadFile(final File file) throws IOException, JsonException {
        return Convert(Files.toString(file, Charsets.UTF_8));
    }

    public static void SaveFile(final File file, final CompoundNBT compound) throws IOException, JsonException {
        final String json = Convert(compound);
        OutputStreamWriter writer = null;
        try {
            writer = new OutputStreamWriter(new FileOutputStream(file), Charsets.UTF_8);
            writer.write(json);
        }
        finally {
            if (writer != null) {
                writer.close();
            }
        }
    }

    public static void main(final String[] args) {
        final CompoundNBT comp = new CompoundNBT();
        final CompoundNBT comp2 = new CompoundNBT();
        comp2.putByteArray("test", new byte[] { 0, 0, 1, 1, 0 });
        comp.put("comp", (INBT)comp2);
        System.out.println(Convert(comp));
    }

    public static String quoteAndEscape(final String p_193588_0_) {
        final StringBuilder stringbuilder = new StringBuilder("\"");
        for (int i = 0; i < p_193588_0_.length(); ++i) {
            final char c0 = p_193588_0_.charAt(i);
            if (c0 == '\\' || c0 == '\"') {
                stringbuilder.append('\\');
            }
            stringbuilder.append(c0);
        }
        return stringbuilder.append('\"').toString();
    }

    static class JsonLine
    {
        private String line;

        public JsonLine(final String line) {
            this.line = line;
        }

        public void removeComma() {
            if (this.line.endsWith(",")) {
                this.line = this.line.substring(0, this.line.length() - 1);
            }
        }

        public boolean reduceTab() {
            final int length = this.line.length();
            return (length == 1 && (this.line.endsWith("}") || this.line.endsWith("]"))) || (length == 2 && (this.line.endsWith("},") || this.line.endsWith("],")));
        }

        public boolean increaseTab() {
            return this.line.endsWith("{") || this.line.endsWith("[");
        }

        @Override
        public String toString() {
            return this.line;
        }
    }

    /**
     * The unread part of the file is the window {@code [start, end)} of {@code original}.
     * The base class re-created the remaining text with {@code substring} on every cut, so a
     * string value was read in quadratic time: a clone carrying a 140k-character script took
     * 1.5 s of the server tick to load.
     */
    static class JsonFile
    {
        private final String original;
        private int start;
        private int end;

        public JsonFile(final String text) {
            this.original = text;
            this.start = 0;
            this.end = text.length();
        }

        public int keyIndex() {
            boolean hasQuote = false;
            for (int length = this.end - this.start, i = 0; i < length; ++i) {
                final char c = this.original.charAt(this.start + i);
                if (i == 0 && c == '\"') {
                    hasQuote = true;
                }
                else if (hasQuote && c == '\"') {
                    hasQuote = false;
                }
                if (!hasQuote && c == ':') {
                    return i;
                }
            }
            return -1;
        }

        public String cutDirty(final int i) {
            final String s = this.substring(0, i);
            this.start += i;
            return s;
        }

        public String cut(final int i) {
            final String s = this.substring(0, i);
            this.start += i;
            // Same as String.trim() on the remaining text.
            while (this.start < this.end && this.original.charAt(this.start) <= ' ') {
                ++this.start;
            }
            while (this.start < this.end && this.original.charAt(this.end - 1) <= ' ') {
                --this.end;
            }
            return s;
        }

        public String substring(final int beginIndex, final int endIndex) {
            final int length = this.end - this.start;
            if (beginIndex < 0 || beginIndex > endIndex || endIndex > length) {
                throw new StringIndexOutOfBoundsException("begin " + beginIndex + ", end " + endIndex + ", length " + length);
            }
            return this.original.substring(this.start + beginIndex, this.start + endIndex);
        }

        public int indexOf(final String s) {
            final int index = this.original.indexOf(s, this.start);
            if (index < 0 || index + s.length() > this.end) {
                return -1;
            }
            return index - this.start;
        }

        public String getCurrentPos() {
            final int lengthOr = this.original.length();
            final int lengthCur = this.end - this.start;
            final int currentPos = lengthOr - lengthCur;
            final String done = this.original.substring(0, currentPos);
            final String[] lines = done.split("\r\n|\r|\n");
            int pos = 0;
            String line = "";
            if (lines.length > 0) {
                pos = lines[lines.length - 1].length();
                line = this.original.split("\r\n|\r|\n")[lines.length - 1].trim();
            }
            return "Line: " + lines.length + ", Pos: " + pos + ", Text: " + line;
        }

        public boolean startsWith(final String... ss) {
            for (final String s : ss) {
                if (s.length() <= this.end - this.start && this.original.startsWith(s, this.start)) {
                    return true;
                }
            }
            return false;
        }

        public boolean endsWith(final String s) {
            return s.length() <= this.end - this.start && this.original.startsWith(s, this.end - s.length());
        }
    }

    public static class JsonException extends Exception
    {
        public JsonException(final String message, final JsonFile json) {
            super(message + ": " + json.getCurrentPos());
        }
    }
}
