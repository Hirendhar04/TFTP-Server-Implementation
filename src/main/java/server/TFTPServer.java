/**
 * A TFTP (Trivial File Transfer Protocol) server implementation that supports
 * read (RRQ) and write (WRQ) requests over UDP. The server handles file transfers
 * with basic error handling, timeout retries, and file validation.
 */
package src.main.java.server;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Implements a TFTP server that listens for client requests on a specified port
 * and processes file read and write operations.
 */
public class TFTPServer {
    /** The default TFTP server port. */
    private static final int TFTP_PORT = 1234;

    /** The size of the buffer for receiving packets. */
    private static final int BUFFER_SIZE = 516;

    /** Directory for reading files. */
    private static final String READ_DIR = "C:/Users/hiren/assignment3/tftpdir/read/";

    /** Directory for writing files. */
    private static final String WRITE_DIR = "C:/Users/hiren/assignment3/tftpdir/write/";

    /** Opcode for Read Request (RRQ). */
    private static final int OP_RRQ = 1;

    /** Opcode for Write Request (WRQ). */
    private static final int OP_WRQ = 2;

    /** Opcode for Data Packet (DATA). */
    private static final int OP_DAT = 3;

    /** Opcode for Acknowledgment (ACK). */
    private static final int OP_ACK = 4;

    /** Opcode for Error Packet (ERROR). */
    private static final int OP_ERR = 5;

    /** Timeout duration for socket operations in milliseconds. */
    private static final int TIMEOUT_MS = 2000;

    /** Maximum number of retries for packet transmission. */
    private static final int MAX_RETRIES = 5;

    /** Maximum allowed file size in bytes (10 MB). */
    private static final int MAX_FILE_SIZE = 10 * 1024 * 1024;

    /** Allowed file extensions for write operations. */
    private static final String[] ALLOWED_EXTENSIONS = {".txt", ".pdf", ".doc", ".docx", ".jpg", ".png", ".ul"};

    /**
     * Entry point for the TFTP server application.
     *
     * @param args Command-line arguments (not used).
     */
    public static void main(String[] args) {
        try {
            new TFTPServer().start();
        } catch (SocketException e) {
            e.printStackTrace();
        }
    }

    /**
     * Starts the TFTP server, listening for incoming client requests and spawning
     * new threads to handle each request.
     *
     * @throws SocketException If there is an error creating the server socket.
     */
    private void start() throws SocketException {
        byte[] buffer = new byte[BUFFER_SIZE];
        DatagramSocket socket = new DatagramSocket(TFTP_PORT);
        System.out.printf("TFTP Server listening on port %d\n", TFTP_PORT);

        while (true) {
            InetSocketAddress clientAddress = receiveFrom(socket, buffer);
            if (clientAddress == null) continue;

            final StringBuilder requestedFile = new StringBuilder();
            final int reqType = parseRequest(buffer, requestedFile);

            new Thread(() -> handleRequest(clientAddress, requestedFile.toString(), reqType)).start();
        }
    }

    /**
     * Receives a packet from the socket and extracts the client's address.
     *
     * @param socket The server socket to receive packets from.
     * @param buffer The buffer to store the received packet data.
     * @return The client's InetSocketAddress, or null if an error occurs.
     */
    private InetSocketAddress receiveFrom(DatagramSocket socket, byte[] buffer) {
        try {
            DatagramPacket receivePacket = new DatagramPacket(buffer, buffer.length);
            socket.receive(receivePacket);
            return new InetSocketAddress(receivePacket.getAddress(), receivePacket.getPort());
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Parses the incoming TFTP request packet to determine the request type and
     * requested file name.
     *
     * @param buffer The packet data buffer.
     * @param requestedFile StringBuilder to store the extracted file name.
     * @return The opcode of the request (OP_RRQ, OP_WRQ, or -1 for invalid mode).
     */
    private int parseRequest(byte[] buffer, StringBuilder requestedFile) {
        ByteBuffer wrap = ByteBuffer.wrap(buffer);
        int opcode = wrap.getShort() & 0xFFFF;
        if (opcode != OP_RRQ && opcode != OP_WRQ) {
            return opcode;
        }
        int index = 2;
        while (index < buffer.length && buffer[index] != 0) {
            requestedFile.append((char) buffer[index]);
            index++;
        }
        index++;
        StringBuilder mode = new StringBuilder();
        while (index < buffer.length && buffer[index] != 0) {
            mode.append((char) buffer[index]);
            index++;
        }
        if (!mode.toString().equalsIgnoreCase("octet")) {
            return -1;
        }
        return opcode;
    }

    /**
     * Handles a client request by processing read or write operations.
     *
     * @param clientAddress The client's address.
     * @param fileName The requested file name.
     * @param requestType The type of request (OP_RRQ or OP_WRQ).
     */
    private void handleRequest(InetSocketAddress clientAddress, String fileName, int requestType) {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.connect(clientAddress);
            String fullPath = (requestType == OP_RRQ) ? READ_DIR + fileName : WRITE_DIR + fileName;

            System.out.printf("%s request for %s from %s:%d\n",
                    (requestType == OP_RRQ) ? "Read" : "Write",
                    fileName, clientAddress.getHostName(), clientAddress.getPort());

            if (requestType == OP_RRQ) {
                File file = new File(fullPath);
                if (!file.exists()) {
                    sendError(socket, 1, "File not found");
                    return;
                }
                if (!file.canRead()) {
                    sendError(socket, 2, "Access violation");
                    return;
                }
                sendDataReceiveAck(socket, fullPath);
            } else if (requestType == OP_WRQ) {
                if (!validateFile(fullPath)) {
                    sendError(socket, 0, "Invalid file type");
                    return;
                }
                File file = new File(fullPath);
                if (file.exists()) {
                    sendError(socket, 6, "File already exists");
                    return;
                }
                File writeDir = new File(WRITE_DIR);
                if (!writeDir.exists() || !writeDir.isDirectory() || !writeDir.canWrite()) {
                    sendError(socket, 2, "Access violation: Cannot write to directory");
                    return;
                }
                ByteBuffer ackPacket = ByteBuffer.allocate(4);
                ackPacket.putShort((short) OP_ACK);
                ackPacket.putShort((short) 0);
                socket.send(new DatagramPacket(ackPacket.array(), ackPacket.limit(),
                        clientAddress.getAddress(), clientAddress.getPort()));
                System.out.println("Sent initial ACK for WRQ: block 0");
                receiveDataSendAck(socket, fullPath);
            } else {
                sendError(socket, 4, "Illegal TFTP operation");
            }
        } catch (SocketException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Sends file data to the client and waits for ACK packets.
     *
     * @param socket The socket connected to the client.
     * @param filePath The path of the file to send.
     * @return True if the file was sent successfully, false otherwise.
     */
    private boolean sendDataReceiveAck(DatagramSocket socket, String filePath) {
        try (FileInputStream fis = new FileInputStream(filePath)) {
            byte[] buffer = new byte[512];
            int blockNumber = 1;
            int bytesRead;

            while ((bytesRead = fis.read(buffer)) != -1) {
                ByteBuffer dataPacket = ByteBuffer.allocate(4 + bytesRead);
                dataPacket.putShort((short) OP_DAT);
                dataPacket.putShort((short) blockNumber);
                dataPacket.put(buffer, 0, bytesRead);

                int retries = 0;
                boolean ackReceived = false;
                while (!ackReceived && retries < MAX_RETRIES) {
                    socket.send(new DatagramPacket(dataPacket.array(), dataPacket.limit()));
                    socket.setSoTimeout(TIMEOUT_MS);
                    try {
                        byte[] ackBuf = new byte[BUFFER_SIZE];
                        DatagramPacket ackPacket = new DatagramPacket(ackBuf, ackBuf.length);
                        socket.receive(ackPacket);
                        ByteBuffer wrap = ByteBuffer.wrap(ackPacket.getData(), 0, ackPacket.getLength());
                        int opcode = wrap.getShort() & 0xFFFF;
                        if (opcode == OP_ACK) {
                            int receivedBlockNumber = wrap.getShort() & 0xFFFF;
                            if (receivedBlockNumber == blockNumber) {
                                ackReceived = true;
                            } else {
                                retries++;
                            }
                        } else if (opcode == OP_ERR) {
                            return false;
                        }
                    } catch (SocketTimeoutException e) {
                        retries++;
                    }
                }
                if (retries >= MAX_RETRIES) {
                    sendError(socket, 0, "Timeout waiting for ACK");
                    return false;
                }
                blockNumber++;
            }
            return true;
        } catch (IOException e) {
            sendError(socket, 2, "Access violation");
            return false;
        }
    }

    /**
     * Receives data packets from the client and sends ACK packets, writing the
     * data to a file.
     *
     * @param socket The socket connected to the client.
     * @param filePath The path of the file to write to.
     * @return True if the file was received successfully, false otherwise.
     */
    private boolean receiveDataSendAck(DatagramSocket socket, String filePath) {
        try (FileOutputStream fos = new FileOutputStream(filePath)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            boolean receiving = true;
            int expectedBlockNumber = 1;

            while (receiving) {
                socket.setSoTimeout(TIMEOUT_MS);
                DatagramPacket receivePacket = new DatagramPacket(buffer, buffer.length);
                try {
                    socket.receive(receivePacket);
                } catch (SocketTimeoutException e) {
                    sendError(socket, 0, "Timeout waiting for data");
                    return false;
                }

                ByteBuffer wrap = ByteBuffer.wrap(receivePacket.getData(), 0, receivePacket.getLength());
                int opcode = wrap.getShort() & 0xFFFF;
                if (opcode == OP_ERR) {
                    return false;
                }
                if (opcode != OP_DAT) {
                    sendError(socket, 4, "Illegal TFTP operation");
                    return false;
                }

                int receivedBlockNumber = wrap.getShort() & 0xFFFF;
                byte[] data = Arrays.copyOfRange(receivePacket.getData(), 4, receivePacket.getLength());
                System.out.printf("Received DATA packet: block %d, size %d bytes\n", receivedBlockNumber, data.length);

                if (receivedBlockNumber == expectedBlockNumber) {
                    fos.write(data);
                    fos.flush();
                    expectedBlockNumber++;
                    System.out.printf("Wrote data for block %d to file\n", receivedBlockNumber);
                } else {
                    System.out.printf("Received unexpected block %d, expected %d\n", receivedBlockNumber, expectedBlockNumber);
                }

                if (new File(filePath).length() > MAX_FILE_SIZE) {
                    sendError(socket, 0, "File exceeds size limit");
                    return false;
                }

                ByteBuffer ackPacket = ByteBuffer.allocate(4);
                ackPacket.putShort((short) OP_ACK);
                ackPacket.putShort((short) receivedBlockNumber);
                socket.send(new DatagramPacket(ackPacket.array(), ackPacket.limit(),
                        receivePacket.getAddress(), receivePacket.getPort()));
                System.out.printf("Sent ACK for block %d\n", receivedBlockNumber);

                if (data.length < 512) {
                    receiving = false;
                    System.out.println("Transfer complete: received final packet");
                }
            }
            return true;
        } catch (IOException e) {
            System.out.println("IOException in receiveDataSendAck: " + e.getMessage());
            sendError(socket, 2, "Access violation");
            return false;
        }
    }

    /**
     * Sends an error packet to the client with the specified error code and message.
     *
     * @param socket The socket connected to the client.
     * @param errorCode The TFTP error code.
     * @param message The error message.
     */
    private void sendError(DatagramSocket socket, int errorCode, String message) {
        try {
            byte[] msgBytes = message.getBytes();
            ByteBuffer errorPacket = ByteBuffer.allocate(4 + msgBytes.length + 1);
            errorPacket.putShort((short) OP_ERR);
            errorPacket.putShort((short) errorCode);
            errorPacket.put(msgBytes);
            errorPacket.put((byte) 0);
            socket.send(new DatagramPacket(errorPacket.array(), errorPacket.limit()));
            System.out.printf("Sent ERROR: code %d, message '%s'\n", errorCode, message);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Validates the file extension for write operations.
     *
     * @param filename The name of the file to validate.
     * @return True if the file extension is allowed, false otherwise.
     */
    private boolean validateFile(String filename) {
        boolean valid = Arrays.stream(ALLOWED_EXTENSIONS).anyMatch(filename::endsWith);
        System.out.printf("File validation for %s: %s\n", filename, valid ? "Valid" : "Invalid");
        return valid;
    }
}