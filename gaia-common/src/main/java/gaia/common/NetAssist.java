package gaia.common;


import static gaia.common.ResourceAssist.stringComposer;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.SocketAddress;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gaia.ConstValues;
import gaia.common.ResourceAssist.StringComposer;
import gaia.common.StringAssist.SplitPattern;


public interface NetAssist {
	Logger log = LoggerFactory.getLogger(NetAssist.class);
	Pattern pure_digit_address = Pattern.compile("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$");

	URLStreamHandler DEFAULT_STREAM_HANDLER = new URLStreamHandler() {
		@Override
		protected URLConnection openConnection(URL u) {
			return null;
		}
	};

	String LOCAL_HOST = FunctionAssist.supply(() -> InetAddress.getLocalHost()
	                                                           .getHostAddress());

	/**
	 * IP base String 을 InetAddress로 전환한다.
	 *
	 * @param address IP base Host Name
	 * @return java.net.InetAddress
	 * @throws UnknownHostException 엉뚱한 도메인을 넣거나 Domain Name base String을 잘 못 넣으면 발
	 */
	static InetAddress getInetAddress(String address) {
		return pureDigitAddress(address).map(bytes -> FunctionAssist.supply(() -> InetAddress.getByAddress(bytes)))
		                                .orElse(FunctionAssist.supply(() -> InetAddress.getByName(address)));
	}

	static Optional<byte[]> pureDigitAddress(SocketAddress socketAddress) {
		return socketAddress instanceof InetSocketAddress
		       ? Optional.of(((InetSocketAddress) socketAddress).getAddress()
		                                                        .getAddress())
		       : Optional.empty();
	}

	static Optional<byte[]> pureDigitAddress(String address) {
		final Matcher matcher = pure_digit_address.matcher(address);

		return matcher.find()
		       ? Optional.of(new byte[]{(byte) Short.parseShort(matcher.group(1)),
		                                (byte) Short.parseShort(matcher.group(2)),
		                                (byte) Short.parseShort(matcher.group(3)),
		                                (byte) Short.parseShort(matcher.group(4))})
		       : Optional.empty();
	}

	static String toString(byte[] addresses) {
		return stringComposer().self(builder -> appendAddress(addresses,
		                                                      builder))
		                       .build();
	}

	static void appendAddress(byte[] addresses,
	                          StringComposer builder) {
		final char sep = addresses.length == 4
		                 ? '.'
		                 : ':';
		for (byte address : addresses) {
			builder.add(address & 0xFF)
			       .add(sep);

		}
		builder.length(1);
	}

	static String getRemoteAddressAsString(SocketAddress socketAddress) {
		return socketAddress instanceof InetSocketAddress
		       ? toString(((InetSocketAddress) socketAddress).getAddress()
		                                                     .getAddress())
		       : socketAddress.toString();
	}

	static byte[] getRemoteAddress(SocketAddress socketAddress) {
		return socketAddress instanceof InetSocketAddress
		       ? ((InetSocketAddress) socketAddress).getAddress()
		                                            .getAddress()
		       : ConstValues.NULL_BYTES;
	}

	static boolean compare(final SocketAddress socketAddress,
	                       final byte[] address,
	                       final int port) {
		if (socketAddress instanceof InetSocketAddress) {
			InetSocketAddress inetSocketAddress = ((InetSocketAddress) socketAddress);
			InetAddress inetAddress = ((InetSocketAddress) socketAddress).getAddress();

			if (port > 0 && inetSocketAddress.getPort() != port) {
				return false;
			}

			return ArrayAssist.compare(address,
			                           inetAddress.getAddress());
		}
		return false;
	}

	static AddressMatcherBuilder matcher() {
		return new AddressMatcherBuilder();
	}

	final class AddressMatcherBuilder {
		private AddressMatcherBuilder() {
		}

		final List<Predicate<byte[]>> allow = new ArrayList<>();
		final List<Predicate<byte[]>> deny = new ArrayList<>();
		boolean allowAll = false;
		boolean denyAll = false;

		public AddressMatcherBuilder allowAll() {
			this.allow.clear();
			this.allowAll = true;
			return this;
		}

		public AddressMatcherBuilder denyAll() {
			this.deny.clear();
			this.denyAll = true;
			return this;
		}

		@SuppressWarnings("UnusedReturnValue")
		public AddressMatcherBuilder allow(final String source) {
			return StringUtils.equals(source,
			                          "*")
			       ? this.allowAll()
			       : this.allow(bytes -> StringUtils.equals(NetAssist.toString(bytes),
			                                                source));
		}

		@SuppressWarnings("UnusedReturnValue")
		public AddressMatcherBuilder allow(final Pattern pattern) {
			return this.allow(bytes -> pattern.matcher(NetAssist.toString(bytes))
			                                  .find());
		}

		public AddressMatcherBuilder allow(final Predicate<byte[]> predicate) {
			this.allowAll = false;
			this.allow.add(predicate);
			return this;
		}

		@SuppressWarnings("UnusedReturnValue")
		public AddressMatcherBuilder deny(final String source) {
			return StringUtils.equals(source,
			                          "*")
			       ? this.denyAll()
			       : this.deny(bytes -> StringUtils.equals(NetAssist.toString(bytes),
			                                               source));
		}

		@SuppressWarnings("UnusedReturnValue")
		public AddressMatcherBuilder deny(final Pattern pattern) {
			return this.deny(bytes -> pattern.matcher(NetAssist.toString(bytes))
			                                 .find());
		}

		public AddressMatcherBuilder deny(final Predicate<byte[]> predicate) {
			this.denyAll = false;
			this.deny.add(predicate);
			return this;
		}

		public void allowPackage(String packageSource) {
			SplitPattern.space.stream(packageSource)
			                  .forEach(part -> {
				                  if (part.charAt(0) == '$') {
					                  // as regular expression
					                  this.allow(Pattern.compile(part.substring(1)));
				                  } else if (StringUtils.equalsIgnoreCase(part,
				                                                          "self")) {
					                  this.allow(NetAssist.LOCAL_HOST);
				                  } else if (StringUtils.equalsIgnoreCase(part,
				                                                          "loop-back")) {
					                  this.allow("127.0.0.1");
				                  } else {
					                  this.allow(part);
				                  }
			                  });
		}

		public void denyPackage(String packageSource) {
			SplitPattern.space.stream(packageSource)
			                  .forEach(part -> {
				                  if (part.charAt(0) == '$') {
					                  // as regular expression
					                  this.deny(Pattern.compile(part.substring(1)));
				                  } else if (StringUtils.equalsIgnoreCase(part,
				                                                          "self")) {
					                  this.deny(NetAssist.LOCAL_HOST);
				                  } else if (StringUtils.equalsIgnoreCase(part,
				                                                          "loop-back")) {
					                  this.deny("127.0.0.1");
				                  } else {
					                  this.deny(part);
				                  }
			                  });
		}

		public Predicate<byte[]> build(boolean allowFirst) {
			final Predicate<byte[]> allowPredicate = this.allowAll
			                                         ? bytes -> true
			                                         : compose(this.allow);
			final Predicate<byte[]> denyPredicate = this.denyAll
			                                        ? bytes -> true
			                                        : compose(this.deny);

			return allowFirst
			       ? allowPredicate.or(denyPredicate.negate())
			       : denyPredicate.negate()
			                      .and(allowPredicate);
		}

		static Predicate<byte[]> compose(List<Predicate<byte[]>> predicates) {
			if (predicates.isEmpty()) return bytes -> false;
			AtomicReference<Predicate<byte[]>> reference = new AtomicReference<>();

			predicates.forEach(predicate -> reference.updateAndGet(before -> Objects.isNull(before)
			                                                                 ? predicate
			                                                                 : before.or(predicate)));

			return reference.get();
		}
	}

	Set<String> SSL_DETECTOR = SplitPattern.common.set("https,wss");

	static boolean needSsl(URL url) {
		return needSsl(String.valueOf(url.getProtocol())
		                     .toLowerCase());
	}

	static boolean needSsl(String scheme) {
		return SSL_DETECTOR.contains(scheme);
	}

	static boolean needSsl(URI uri) {
		return SSL_DETECTOR.contains(String.valueOf(uri.getScheme())
		                                   .toLowerCase());
	}

	Set<String> WEB_SOCKET_DETECTOR = SplitPattern.common.set("ws,wss");

	static boolean needWebSocketHandler(URL url) {
		return WEB_SOCKET_DETECTOR.contains(String.valueOf(url.getProtocol())
		                                          .toLowerCase());
	}

	static boolean needWebSocketHandler(URI uri) {
		return WEB_SOCKET_DETECTOR.contains(String.valueOf(uri.getScheme())
		                                          .toLowerCase());
	}

	static int getPort(URI uri) {
		if (uri.getPort() > 0) {
			return uri.getPort();
		}

		final String schema = String.valueOf(uri.getScheme())
		                            .toLowerCase();

		switch (schema) {
			case "http":
			case "ws": {
				return 80;
			}

			case "https":
			case "wss": {
				return 443;
			}
		}
		return -1;
	}

	static URI transferWebSocketURI(URI source) {
		final AtomicReference<String> schema = new AtomicReference<>(source.getScheme()
		                                                                   .toLowerCase());
		final AtomicReference<Integer> port = new AtomicReference<>(source.getPort());
		switch (schema.get()) {
			case "http": {
				schema.set("ws");
				port.updateAndGet(before -> before < 0
				                            ? 80
				                            : before);
				break;
			}
			case "https": {
				schema.set("wss");
				port.updateAndGet(before -> before < 0
				                            ? 80
				                            : before);
				break;
			}
		}

		return FunctionAssist.supply(() -> new URI(schema.get(),
		                                           source.getUserInfo(),
		                                           source.getHost(),
		                                           port.get(),
		                                           source.getPath(),
		                                           source.getQuery(),
		                                           source.getFragment()));
	}

	@Deprecated
	static URL transferWebSocketURL(URL source) {
		final String sourceProtocol = source.getProtocol()
		                                    .toLowerCase();
		final AtomicReference<String> targetProtocol = new AtomicReference<>(sourceProtocol);
		final int targetPort = source.getPort() < 0
		                       ? source.getDefaultPort()
		                       : source.getPort();
		switch (sourceProtocol) {
			case "http": {
				targetProtocol.set("ws");
				break;
			}
			case "https": {
				targetProtocol.set("wss");
				break;
			}
		}

		return FunctionAssist.supply(() -> new URL(targetProtocol.get(),
		                                           source.getHost(),
		                                           targetPort,
		                                           source.getFile(),
		                                           DEFAULT_STREAM_HANDLER));
	}

	/**
	 * IP base Address를 InetAddress로 전환 가능한 byte array로 전환한다.
	 *
	 * @param addr IP base address
	 * @return byte array
	 */
	static byte[] getByteArray(String addr) {
		try {
			final String[] elms = StringAssist.split(addr,
			                                         '.',
			                                         true,
			                                         false);

			if (elms.length == 4) {
				byte[] returnValue = new byte[4];
				returnValue[0] = (byte) (Integer.parseInt(elms[0]) & 0xFF);
				returnValue[1] = (byte) (Integer.parseInt(elms[1]) & 0xFF);
				returnValue[2] = (byte) (Integer.parseInt(elms[2]) & 0xFF);
				returnValue[3] = (byte) (Integer.parseInt(elms[3]) & 0xFF);
				return returnValue;
			} else {
				if (log.isWarnEnabled()) {
					log.warn(StringAssist.format("row ip element invalid [%s]",
					                             addr));
				}
			}
		}
		catch (Exception thw) {
			return null;
		}
		return null;
	}

	/**
	 * IP base Address를 InetAddress로 전환 가능한 int array로 전환한다.
	 *
	 * @param addr IP base address
	 * @return int array
	 */
	static byte[] getIntArray(String addr) {
		try {
			final String[] elms = StringAssist.split(addr,
			                                         '.',
			                                         true,
			                                         true);

			if (elms.length == 4) {
				return new byte[]{(byte) (Integer.parseInt(elms[0]) & 0xFF),
				                  (byte) (Integer.parseInt(elms[1]) & 0xFF),
				                  (byte) (Integer.parseInt(elms[2]) & 0xFF),
				                  (byte) (Integer.parseInt(elms[3]) & 0xFF)};
			} else {
				log.warn(StringAssist.format("NetAddress getRawAddr() invalid [%s]",
				                             addr));
			}
		}
		catch (Exception thw) {
			//
		}
		return null;
	}

	/**
	 * IP address를 나타내는 byte array를 IP base String으로 전환한다.
	 *
	 * @param addr byte array
	 * @return IP base String
	 */
	static String toDottedQuad(byte[] addr) {
		return (addr[0] & 0xFF) + "." + (addr[1] & 0xFF) + "." + (addr[2] & 0xFF) + "." + (addr[3] & 0xFF);
	}

	/**
	 *
	 */
	char CHAR_0 = '0';

	/**
	 *
	 */
	char CHAR_9 = '9';

	/**
	 *
	 */
	char CHAR_DOT = '.';

	/**
	 * @param ip
	 * @return
	 */
	static byte[] getPureDigitIP(String ip) {
		if (isIPv4(ip)) {
			return getIntArray(ip);
		}
		return null;
	}

	static boolean isIPv4(String ip) {
		int dotCnt = 0;
		for (int i = 0; i < ip.length(); i++) {
			char tmpChar = ip.charAt(i);
			if (tmpChar == CHAR_DOT) {
				dotCnt++;
			} else if (tmpChar < CHAR_0 || tmpChar > CHAR_9) {
				return false;
			}
		}

		return dotCnt == 3;
	}

	/**
	 * @param i1
	 * @param i2
	 * @param i3
	 * @param i4
	 * @return
	 */
	static int getIntIPToInetInt(int i1,
	                             int i2,
	                             int i3,
	                             int i4) {
		return (i4 & 0xFF) << 24 | (i3 & 0xFF) << 16 | (i2 & 0xFF) << 8 | (i1 & 0xFF);
	}

	/**
	 * @param iIP
	 * @return
	 */
	static byte[] getIntToInetByteArray(int iIP) {
		byte[] returnValue = new byte[4];

		returnValue[0] = (byte) (iIP & 0xFF);
		returnValue[1] = (byte) (iIP >>> 8 & 0xFF);
		returnValue[2] = (byte) (iIP >>> 16 & 0xFF);
		returnValue[3] = (byte) (iIP >>> 24 & 0xFF);

		return returnValue;
	}

	static String getIntIPToString(int iIP) {
		if (iIP < 0) {
			return "0.0.0.0";
		}
		return (iIP & 0xFF) + "." + (iIP >>> 8 & 0xFF) + "." + (iIP >>> 16 & 0xFF) + "." + (iIP >>> 24 & 0xFF);
	}

	/**
	 * IP base String 을 InetAddress로 전환한다.
	 *
	 * @param iIP IP base Host Name
	 * @return java.net.InetAddress
	 * @throws UnknownHostException 엉뚱한 도메인을 넣거나 Domain Name base String을 잘 못 넣으면 발
	 */
	static InetAddress getInetAddress(int iIP) throws UnknownHostException {
		byte[] returnValue = getIntToInetByteArray(iIP);
		return returnValue == null
		       ? null
		       : InetAddress.getByAddress(returnValue);
	}

	static int findAvailableServerPort(int start,
	                                   int end) throws UnknownHostException {
		return findAvailableServerPort("127.0.0.1",
		                               start,
		                               end);
	}

	static int findAvailableServerPort(String address,
	                                   int start,
	                                   int end) throws UnknownHostException {
		return findAvailableServerPort(InetAddress.getByName(address),
		                               start,
		                               end);
	}

	static int findAvailableServerPort(InetAddress address,
	                                   int start,
	                                   int end) {
		return IntStream.range(start,
		                       end)
		                .filter(port -> {
			                try {
				                ServerSocket socket = new ServerSocket(port,
				                                                       0,
				                                                       address);

				                if (log.isTraceEnabled()) {
					                log.trace(StringAssist.format("[%s] bind test success",
					                                              port));
				                }

				                ObjectFinalizer.close(socket);
			                }
			                catch (IOException ioe) {
				                return false;
			                }
			                return true;
		                })
		                .findFirst()
		                .orElseThrow(() -> new IllegalStateException(StringAssist.format("cannot find available port of [%s / start / end]",
		                                                                                 address,
		                                                                                 start,
		                                                                                 end)));
	}
}
