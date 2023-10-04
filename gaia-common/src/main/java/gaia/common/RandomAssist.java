package gaia.common;

import static gaia.common.ResourceAssist.stringComposer;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author dragon
 */
public interface RandomAssist {
	Logger log = LoggerFactory.getLogger(RandomAssist.class);

	/**
	 *
	 */
	char[] RANGE_CAN_NAMED = FunctionAssist.supply("0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ-_"::toCharArray);

	/**
	 *
	 */
	int RANGE_CAN_NAMED_MARKER = 0x3F;
	Random rd = new Random(System.currentTimeMillis());
	AtomicInteger uniqueSequence = new AtomicInteger(0);
	AtomicLong uniqueNumber = new AtomicLong(System.nanoTime());

	/**
	 *
	 */
	char[] RANGE_CHARACTER_ONLY = FunctionAssist.supply("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"::toCharArray);

	/**
	 *
	 */
	char[] RANGE_SPECIAL_EXCEPT = FunctionAssist.supply("0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"::toCharArray);

	/**
	 *
	 */
	char[] RANGE_NUMERIC_ONLY = FunctionAssist.supply("0123456789"::toCharArray);

	/**
	 *
	 */
	char[] RANGE_SPECIAL_ONLY = FunctionAssist.supply("`~!@#$%^&*()-_=+\\|[{]};:'\",<.>/?"::toCharArray);

	/**
	 *
	 */
	char[] RANGE_MIXED = FunctionAssist.supply("abcdefghijklmnopqrstuvwxyz0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ`~!@#$%^&*()-_=+\\|[{]};:'\",<.>/?"::toCharArray);


	/**
	 *
	 */
	static String randomString(int length) {
		long number = rd.nextLong() % 10 ^ length;

		return StringAssist.lpad(String.valueOf(number),
		                         '0',
		                         length);
	}

	/**
	 *
	 */
	static int getRandomNumber() {
		return rd.nextInt();
	}

	/**
	 *
	 */
	static float getRandomFloat() {
		return getRandomFloat(10000,
		                      100F);
	}

	/**
	 *
	 */
	static float getRandomFloat(int div,
	                            float by) {
		return Math.abs(rd.nextInt()) % div / by;
	}

	/**
	 *
	 */
	static int getRandomNumber(int bound) {
		return Math.abs(rd.nextInt(bound));
	}

	/**
	 *
	 */
	static int getNextSequence() {
		return uniqueSequence.getAndIncrement();
	}

	/**
	 *
	 */
	static String getUniqueHexNumber() {
		return StringAssist.format("%sZ%s",
		                           Long.toHexString(System.currentTimeMillis()),
		                           Long.toHexString(uniqueNumber.getAndIncrement()));
	}

	/**
	 *
	 */
	static String getUUID() {
		return generateUUID().toString();
	}

	static UUID generateUUID() {
		return new UUID(System.currentTimeMillis(),
		                uniqueNumber.getAndIncrement());
	}

	/**
	 *
	 */
	static long getNextNumber() {
		return uniqueNumber.getAndIncrement();
	}

	/**
	 *
	 */
	static String getUniqueString() {
		return longToString(uniqueNumber.getAndIncrement());
	}

	/**
	 *
	 */
	static String longToString(final long value) {
		return stringComposer().add(builder -> {
			                       long val = value;

			                       while (val != 0) {
				                       int cov = (int) (val & RANGE_CAN_NAMED_MARKER);
				                       builder.insert(0,
				                                      RANGE_CAN_NAMED[cov]);
				                       val = val >>> 6;
			                       }
		                       })
		                       .build();
	}

	/**
	 *
	 */
	static long stringToLong(final String value) {
		long val = 0;

		for (final char elm : value.toCharArray()) {
			int conv = ArrayAssist.findArrayIndex(RANGE_CAN_NAMED,
			                                      elm);

			if (conv < 0) {
				throw new Error(StringAssist.format("invalid long string [%s]",
				                                    elm));
			}

			val = val << 6 | conv;
		}

		return val;

	}

	enum RandomType {
		/**
		 *
		 */
		numeric(RANGE_NUMERIC_ONLY),
		/**
		 *
		 */
		character(RANGE_CHARACTER_ONLY),
		/**
		 *
		 */
		special(RANGE_SPECIAL_ONLY),
		/**
		 *
		 */
		specialExcept(RANGE_SPECIAL_EXCEPT),
		/**
		 *
		 */
		all(RANGE_MIXED);

		final char[] characters;

		RandomType(char[] range) {
			this.characters = range;
		}

		public String random(final int length) {
			return stringComposer()
					.add(buffer -> {
						final int range = this.characters.length;
						int seed = 0;
						for (int i = 0; i < length; i++) {
							if (i % 8 == 0) {
								seed = Math.abs(rd.nextInt());
							}
							buffer.append(this.characters[(seed >> i % 8) % range]);
						}
					})
					.build();
		}
	}
}
