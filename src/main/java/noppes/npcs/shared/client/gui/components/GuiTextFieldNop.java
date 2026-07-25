package noppes.npcs.shared.client.gui.components;

import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import noppes.npcs.shared.client.gui.listeners.ITextfieldListener;

public class GuiTextFieldNop extends TextFieldWidget {
   public boolean enabled = true;
   public boolean inMenu = true;
   public boolean numbersOnly = false;
   public boolean floatsOnly = false;
   public ITextfieldListener listener;
   public int id;
   public int min = 0;
   public int max = Integer.MAX_VALUE;
   public int def = 0;
   public float minF = 0.0f;
   public float maxF = Float.MAX_VALUE;
   public float defF = 0.0f;
   private static GuiTextFieldNop activeTextfield = null;
   private final int[] allowedSpecialChars = new int[]{14, 211, 203, 205};

   public GuiTextFieldNop(int id, Screen parent, int i, int j, int k, int l, String s) {
      this(id, parent, i, j, k, l, new TranslationTextComponent(s != null ? s : ""));
   }

   public GuiTextFieldNop(int id, Screen parent, int i, int j, int k, int l, ITextComponent s) {
      super(Minecraft.getInstance().font, i, j, k, l, s);
      this.setMaxLength(500);
      if (!s.getString().isEmpty()) {
         this.setValue(s.getString());
      }

      this.id = id;
      if (parent instanceof ITextfieldListener) {
         this.listener = (ITextfieldListener)parent;
      }
   }

   public static boolean isActive() {
      return activeTextfield != null;
   }

   public static GuiTextFieldNop getActive() {
      return activeTextfield;
   }

   private boolean charAllowed(char c, int i) {
      if (this.floatsOnly) {
         if (Character.isDigit(c) || c == '.' || c == '-') {
            return true;
         }
         for (int j : this.allowedSpecialChars) {
            if (j == i) {
               return true;
            }
         }
         return false;
      }
      if (this.numbersOnly && !Character.isDigit(c)) {
         for (int j : this.allowedSpecialChars) {
            if (j == i) {
               return true;
            }
         }

         return false;
      } else {
         return true;
      }
   }

   public boolean charTyped(char c, int i) {
      return !this.charAllowed(c, i) ? false : super.charTyped(c, i);
   }

   public boolean isEmpty() {
      return this.getValue().trim().length() == 0;
   }

   public int getInteger() {
      return Integer.parseInt(this.getValue());
   }

   public boolean isInteger() {
      try {
         Integer.parseInt(this.getValue());
         return true;
      } catch (NumberFormatException var2) {
         return false;
      }
   }

   public float getFloat() {
      return Float.parseFloat(this.getValue().trim());
   }

   public boolean isFloat() {
      try {
         Float.parseFloat(this.getValue().trim());
         return true;
      } catch (NumberFormatException e) {
         return false;
      }
   }

   public boolean mouseClicked(double i, double j, int k) {
      if (!this.enabled) {
         return false;
      } else {
         boolean wasFocused = this.isFocused();
         boolean clicked = super.mouseClicked(i, j, k);
         if (!wasFocused && this.isFocused()) {
            unfocus();
            activeTextfield = this;
         }

         if (wasFocused && !this.isFocused()) {
            this.unFocused();
         }

         return clicked;
      }
   }

   public void unFocused() {
      if (this.floatsOnly) {
         if (!this.isEmpty() && this.isFloat()) {
            float value = this.getFloat();
            if (value < this.minF) {
               this.setValue(formatFloat(this.minF));
            } else if (value > this.maxF) {
               this.setValue(formatFloat(this.maxF));
            } else {
               this.setValue(formatFloat(value));
            }
         } else {
            this.setValue(formatFloat(this.defF));
         }
      } else if (this.numbersOnly) {
         if (!this.isEmpty() && this.isInteger()) {
            if (this.getInteger() < this.min) {
               this.setValue(this.min + "");
            } else if (this.getInteger() > this.max) {
               this.setValue(this.max + "");
            }
         } else {
            this.setValue(this.def + "");
         }
      }

      if (this.listener != null) {
         this.listener.unFocused(this);
      }

      this.setFocus(false);
      if (this == activeTextfield) {
         activeTextfield = null;
      }
   }

   public void renderButton(MatrixStack matrixStack, int x, int y, float f) {
      if (this.enabled) {
         super.renderButton(matrixStack, y, x, f);
      }
   }

   public void setMinMaxDefault(int i, int j, int k) {
      this.min = i;
      this.max = j;
      this.def = k;
   }

   public void setMinMaxDefault(float min, float max, float def) {
      this.minF = min;
      this.maxF = max;
      this.defF = def;
   }

   public static String formatFloat(float value) {
      if (value == (long) value) {
         return Long.toString((long) value);
      }
      return Float.toString(value);
   }

   public static void unfocus() {
      GuiTextFieldNop field = activeTextfield;
      activeTextfield = null;
      if (field != null) {
         field.unFocused();
      }
   }

   public GuiTextFieldNop setNumbersOnly() {
      this.numbersOnly = true;
      return this;
   }

   public GuiTextFieldNop setFloatsOnly() {
      this.floatsOnly = true;
      return this;
   }
}
