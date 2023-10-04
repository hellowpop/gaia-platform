package gaia;

import java.lang.reflect.Modifier;

/**
 * @author dragon
 */
public interface ConstValues {
	String BLANK_STRING = "";
	Object[] NULL_OBJECTS = new Object[0];
	String[] BLANK_STRING_ARRAY = new String[]{};
	Class<?>[] NULL_CLASS = new Class[0];
	byte[] NULL_BYTES = new byte[0];
	char CHAR_CR = '\r';
	char CHAR_LF = '\n';
	char CHAR_TAB = '\t';
	char CHAR_NULL = '\0';
	char CHAR_COMMA = ',';
	char CHAR_BLANK = ' ';
	char CHAR_MINUS = '-';
	char CHAR_SINGLE_QUOTATION = '\'';
	char CHAR_DOUBLE_QUOTATION = '"';
	char[] NEW_LINE_CHARS = {ConstValues.CHAR_CR,
	                         ConstValues.CHAR_LF};
	byte BYTE_CR = (byte) ConstValues.CHAR_CR;
	byte BYTE_LF = (byte) ConstValues.CHAR_LF;
	byte BYTE_DOUBLE_QUOTATION = (byte) ConstValues.CHAR_DOUBLE_QUOTATION;
	byte BYTE_MINUS = (byte) ConstValues.CHAR_MINUS;
	byte TAB_BYTE = 9;
	byte SPACE_BYTE = 32;
	byte BYTE_EQUAL = '=';
	byte BYTE_AND = '&';
	byte[] NEW_LINE_BYTES = {BYTE_CR,
	                         BYTE_LF};
	int MOD_MASK_PUBLIC_PROTECTED = Modifier.PUBLIC | Modifier.PROTECTED;


	class CrashedException
			extends RuntimeException {
		private static final long serialVersionUID = 8665880566862314866L;

		public CrashedException() {
			this("this is FATAL case!");
		}

		public CrashedException(String message) {
			super(message);
		}

		public CrashedException(String message,
		                        Throwable cause) {
			super(message,
			      cause);
		}
	}
}
