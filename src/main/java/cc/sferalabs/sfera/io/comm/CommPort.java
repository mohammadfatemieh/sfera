/*-
 * +======================================================================+
 * Sfera
 * ---
 * Copyright (C) 2015 - 2016 Sfera Labs S.r.l.
 * ---
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 * 
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-3.0.html>.
 * -======================================================================-
 */

package cc.sferalabs.sfera.io.comm;

import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jssc.SerialPort;

/**
 * Utility class for serial communication. It provides an abstraction layer for
 * seamless use of serial ports available on the host machine and remote IP
 * gateways.
 * 
 * @author Giampiero Baggiani
 *
 * @version 1.0.0
 *
 */
public abstract class CommPort {

	private static final Logger logger = LoggerFactory.getLogger(CommPort.class);

	public static final int PARITY_NONE = SerialPort.PARITY_NONE;
	public static final int PARITY_ODD = SerialPort.PARITY_ODD;
	public static final int PARITY_EVEN = SerialPort.PARITY_EVEN;
	public static final int PARITY_MARK = SerialPort.PARITY_MARK;
	public static final int PARITY_SPACE = SerialPort.PARITY_SPACE;

	public static final int STOPBITS_1 = SerialPort.STOPBITS_1;
	public static final int STOPBITS_2 = SerialPort.STOPBITS_2;
	public static final int STOPBITS_1_5 = SerialPort.STOPBITS_1_5;

	public static final int FLOWCONTROL_NONE = SerialPort.FLOWCONTROL_NONE;

	public static final int FLOWCONTROL_RTSCTS = SerialPort.FLOWCONTROL_RTSCTS_IN | SerialPort.FLOWCONTROL_RTSCTS_OUT;

	public static final int FLOWCONTROL_XONXOFF = SerialPort.FLOWCONTROL_XONXOFF_IN
			| SerialPort.FLOWCONTROL_XONXOFF_OUT;

	private static final Map<String, CommPort> openLocalPorts = new HashMap<>();

	protected final String portName;
	private int openCount = 0;

	/**
	 * @param portName
	 *            the port name
	 */
	protected CommPort(String portName) {
		this.portName = portName;
	}

	/**
	 * Returns the port name
	 * 
	 * @return the port name
	 */
	public String getPortName() {
		return portName;
	}

	/**
	 * Opens a connection to the specified port. If the specified {@code portName}
	 * has the format {@literal <}hostname{@literal >}:
	 * {@literal <}port{@literal >}, it is considered to be an IP/serial gateway.
	 * Otherwise tries opening a local port with the specified {@code portName}. In
	 * case of a local port, if one with the specified {@code portName} has been
	 * previously open, the same object is returned.
	 * 
	 * @param portName
	 *            the port to connect to. It can be the name of a local serial port
	 *            (e.g. "/dev/ttyS0") or the address of a remote gateway in the
	 *            format {@literal <}hostname{@literal >}:{@literal <}port
	 *            {@literal >} (e.g. "192.168.1.150:4040" or
	 *            "port.example.com:5001").
	 * @return the open {@code CommPort}
	 * @throws CommPortException
	 *             if an error occurs when opening the port
	 */
	public static CommPort open(String portName) throws CommPortException {
		portName = portName.trim();
		CommPort commPort = null;

		if (isIpPort(portName)) {
			commPort = new IPCommPort(portName);
			logger.debug("Created IP comm port '{}'", portName);
		} else {
			synchronized (openLocalPorts) {
				commPort = openLocalPorts.get(portName);
				if (commPort == null) {
					commPort = new LocalCommPort(portName);
					logger.debug("Created local comm port '{}'", portName);
				}
				openLocalPorts.put(portName, commPort);
			}
		}

		synchronized (commPort) {
			if (commPort.openCount > 0) {
				commPort.openCount++;
				return commPort;
			}
			try {
				commPort.doOpen();
				commPort.openCount++;
				logger.debug("Comm port '{}' open", portName);
			} catch (CommPortException e) {
				logger.debug("Error opening comm port '{}': {}", portName, e.getLocalizedMessage());
				if (commPort instanceof LocalCommPort) {
					synchronized (openLocalPorts) {
						openLocalPorts.remove(portName);
					}
				}
				throw e;
			}
			return commPort;
		}
	}

	/**
	 * Returns whether or not this port is open.
	 * 
	 * @return {@code true} if this port is open, {@code false} otherwise
	 */
	public abstract boolean isOpen();

	/**
	 * 
	 * @throws CommPortException
	 *             if an error occurs
	 */
	protected abstract void doOpen() throws CommPortException;

	/**
	 * @param portName
	 * @return
	 */
	private static boolean isIpPort(String portName) {
		int colon = portName.indexOf(':');
		if (colon < 0) {
			return false;
		}
		try {
			String portNum = portName.substring(colon + 1);
			Integer.parseInt(portNum);
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	/**
	 * Decreases the count of the {@link CommPort#open(String)} callers that have
	 * been returned this {@code CommPort}. If zero is reached, the port is
	 * {@link CommPort#close() closed}.
	 * 
	 * @throws CommPortException
	 *             if an error occurs when closing the port
	 */
	public synchronized void release() throws CommPortException {
		openCount--;
		if (openCount <= 0) {
			close();
		}
	}

	/**
	 * Sets the serial communication parameters, if supported by the underlying
	 * device.
	 * 
	 * @param baudRate
	 *            the baud rate
	 * @param dataBits
	 *            number of data bits (from 5 to 8)
	 * @param stopBits
	 *            number of stop bits (use {@code CommPort.STOPBITS_1},
	 *            {@code CommPort.STOPBITS_2} or {@code CommPort.STOPBITS_1_5})
	 * @param parity
	 *            parity (use {@code CommPort.PARITY_NONE},
	 *            {@code CommPort.PARITY_ODD}, {@code CommPort.PARITY_EVEN},
	 *            {@code CommPort.PARITY_MARK} or {@code CommPort.PARITY_SPACE})
	 * @param flowControl
	 *            flow control (use {@code CommPort.FLOWCONTROL_RTSCTS} or
	 *            {@code CommPort.FLOWCONTROL_XONXOFF})
	 * @throws CommPortException
	 *             if an error occurs
	 */
	public abstract void setParams(int baudRate, int dataBits, int stopBits, int parity, int flowControl)
			throws CommPortException;

	/**
	 * Clears the input buffer, if supported by the underlying device
	 * 
	 * @throws CommPortException
	 *             if an error occurs
	 */
	public abstract void clear() throws CommPortException;

	/**
	 * Sets the specified {@code CommPortListener} as the listener for this port.
	 * 
	 * @param listener
	 *            the {@code CommPortListener} to be set
	 * @throws CommPortException
	 *             if an error occurs
	 */
	public abstract void setListener(CommPortListener listener) throws CommPortException;

	/**
	 * Removes the previously set listener.
	 * <p>
	 * This method should be called from the {@link CommPortListener#onRead(byte[])}
	 * method implementation to assure that subsequent data is not consumed from the
	 * listener. Otherwise, after calling this method, the listener may still
	 * consume some data and call {@link CommPortListener#onRead(byte[])} once more.
	 * </p>
	 * 
	 * @throws CommPortException
	 *             if an error occurs
	 */
	public abstract void removeListener() throws CommPortException;

	/**
	 * Returns the number of bytes currently available in the input buffer.
	 * 
	 * @return the number of bytes currently available in the input buffer
	 * @throws CommPortException
	 *             if an error occurs
	 */
	public abstract int getAvailableBytesCount() throws CommPortException;

	/**
	 * Writes the specified byte to this port.
	 * 
	 * @param b
	 *            the byte to write
	 * @throws CommPortException
	 *             if an error occurs
	 */
	public abstract void writeByte(int b) throws CommPortException;

	/**
	 * Writes all the bytes in the specified byte array to this port.
	 * 
	 * @param bytes
	 *            the data to write
	 * @throws CommPortException
	 *             if an error occurs
	 */
	public abstract void writeBytes(byte[] bytes) throws CommPortException;

	/**
	 * Writes all the bytes in the specified array to this port. For each value in
	 * the array the byte sent corresponds to its eight low-order bits.
	 * 
	 * @param bytes
	 *            the data to write
	 * @throws CommPortException
	 *             if an error occurs
	 */
	public abstract void writeBytes(int[] bytes) throws CommPortException;

	/**
	 * Writes the bytes resulting from encoding the specified {@code string} using
	 * the specified {@link Charset} to this port.
	 * 
	 * @param string
	 *            the data to write
	 * @param charset
	 *            the {@link Charset} to be used to encode the data
	 * @throws CommPortException
	 *             if an error occurs
	 */
	public abstract void writeString(String string, Charset charset) throws CommPortException;

	/**
	 * Removes the eventually added listener and closes the connection to the port.
	 * 
	 * @throws CommPortException
	 *             if an error occurs
	 */
	public synchronized void close() throws CommPortException {
		doClose();
		synchronized (openLocalPorts) {
			openLocalPorts.remove(portName);
		}
		openCount = 0;
	}

	/**
	 * 
	 * @throws CommPortException
	 *             if an error occurs
	 */
	protected abstract void doClose() throws CommPortException;

	/**
	 * <p>
	 * Reads up to {@code len} bytes of data from the input stream into the
	 * specified byte array starting from the index specified by {@code offset}. An
	 * attempt is made to read as many as {@code len} bytes, but a smaller number
	 * may be read. The number of bytes actually read is returned.
	 * </p>
	 * <p>
	 * This method blocks until input data is available or it is no longer possible
	 * to read data.
	 * </p>
	 * <p>
	 * Elements {@code b[0]} through {@code b[offset]} and elements
	 * {@code b[offset+len]} through {@code b[b.length-1]} are unaffected.
	 * </p>
	 * 
	 * @param b
	 *            the buffer into which the data is read
	 * @param offset
	 *            the start offset in array {@code b} at which the data is written
	 * @param len
	 *            the maximum number of bytes to read
	 * @return the total number of bytes read into the buffer, or -1 if it was not
	 *         possible to read data
	 * @throws CommPortException
	 *             if an error occurs
	 * @throws NullPointerException
	 *             if {@code b} is null
	 * @throws IndexOutOfBoundsException
	 *             if {@code offset} is negative, {@code len} is negative or
	 *             {@code len} is greater than {@code b.length - offset}
	 */
	public abstract int readBytes(byte[] b, int offset, int len) throws CommPortException;

	/**
	 * Reads data from the input stream as specified by {@link #readBytes} waiting
	 * up to the specified time. If the timeout expires before {@code len} bytes are
	 * read, a {@link CommPortTimeoutException} is raised.
	 * 
	 * @param b
	 *            the buffer into which the data is read
	 * @param offset
	 *            the start offset in array {@code b} at which the data is written
	 * @param len
	 *            the maximum number of bytes to read
	 * @param timeoutMillis
	 *            the specified timeout, in milliseconds
	 * @return the total number of bytes read into the buffer, or -1 if it was not
	 *         possible to read data
	 * @throws CommPortTimeoutException
	 *             if the timeout expires
	 * @throws CommPortException
	 *             if an error occurs
	 * @throws NullPointerException
	 *             if {@code b} is null
	 * @throws IndexOutOfBoundsException
	 *             if {@code offset} is negative, {@code len} is negative or
	 *             {@code len} is greater than {@code b.length - offset}
	 * 
	 */
	public abstract int readBytes(byte[] b, int offset, int len, int timeoutMillis)
			throws CommPortException, CommPortTimeoutException;

}
