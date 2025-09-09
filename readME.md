TFTP Server Implementation
hr222rs(100% of the work)
1. Overview


TFTP is a simple, lightweight protocol for transferring files, typically used in environments requiring minimal overhead (e.g., network booting). Unlike FTP, TFTP uses UDP, lacks authentication, and supports only basic operations: Read Request (RRQ) to download files and Write Request (WRQ) to upload files. This server supports these operations with error handling, timeouts, retries, and file validation.


The server:


Listens on a specified port (1234) for client requests.


Processes RRQ and WRQ requests in separate threads for concurrent handling.


Reads files from a designated directory (READ_DIR) and writes to another (WRITE_DIR).


Enforces rules like allowed file extensions, maximum file size (10MB), and proper packet sequencing.

2. Features



Read Requests (RRQ): Allows clients to download files from the server's read directory.



Write Requests (WRQ): Allows clients to upload files to the server's write directory with file extension validation.



Error Handling: Supports TFTP error codes for issues like file not found, access violation, and illegal operations.



Timeout and Retries: Configurable timeout (2000ms) and maximum retries (5) for reliable packet transmission.



File Validation: Restricts uploads to specific file extensions (.txt, .pdf, .doc, .docx, .jpg, .png, .ul).



File Size Limit: Enforces a maximum file size of 10MB for uploads.



Multi-threaded: Handles multiple client requests concurrently using separate threads.

3. Key Components

Port and Buffer:


TFTP_PORT = 1234: The port where the server listens.


BUFFER_SIZE = 516: Maximum size for TFTP packets (4 bytes header + 512 bytes data).


Directories:


READ_DIR: Directory for files clients can download (C:/Users/hiren/assignment3/tftpdir/read/).


WRITE_DIR: Directory for files clients upload (C:/Users/hiren/assignment3/tftpdir/write/).


Opcodes:


OP_RRQ = 1: Read request.


OP_WRQ = 2: Write request.


OP_DAT = 3: Data packet.


OP_ACK = 4: Acknowledgment packet.


OP_ERR = 5: Error packet.


Timeouts and Limits:


TIMEOUT_MS = 2000: 2-second timeout for packet responses.


MAX_RETRIES = 5: Maximum retries for failed packet transmissions.


MAX_FILE_SIZE = 10 * 1024 * 1024: 10MB limit for uploaded files.


File Validation:


ALLOWED_EXTENSIONS: Restricts uploads to .txt, .pdf, .doc, .docx, .jpg, .png, .ul.


4. Prerequisites

A system with read/write permissions for the specified directories

Directory Structure


Read Directory: C:/Users/hiren/assignment3/tftpdir/read/
Stores files available for download by clients.



Write Directory: C:/Users/hiren/assignment3/tftpdir/write/
Stores files uploaded by clients.

Ensure these directories exist and have appropriate read/write permissions before running the server.

5. How to Run
Clone the Repository or Copy the CodeEnsure the TFTPServer.java file is in the src/main/java/server directory.



Compile the Code

javac src/main/java/server/TFTPServer.java


Run the Server

java -cp src/main/java server.TFTPServer


Test with a TFTP ClientUse a TFTP client (e.g., tftp command-line tool or a GUI client) to connect to the server at localhost on port 1234.


6. Configuration

The server uses the following constants, which can be modified in TFTPServer.java:





TFTP_PORT: Default port (1234)



BUFFER_SIZE: Packet buffer size (516 bytes)



READ_DIR: Directory for downloadable files



WRITE_DIR: Directory for uploaded files



TIMEOUT_MS: Socket timeout (2000ms)



MAX_RETRIES: Maximum retries for packet transmission (5)



MAX_FILE_SIZE: Maximum upload size (10MB)



ALLOWED_EXTENSIONS: Allowed file extensions for uploads



7. summary


The TFTP Server, implemented in Java, enables file transfers over UDP using the Trivial File Transfer Protocol (TFTP). It listens on port 1234 for client read (RRQ) or write (WRQ) requests, handling each in a separate thread for concurrent processing. For read requests, it sends files from a designated read directory in 512-byte chunks, waiting for client acknowledgments. For write requests, it validates file extensions, receives data, and saves files to a write directory, enforcing a 10MB size limit. The server includes timeout retries, error handling for issues like file not found or access violations, and logs key events to the console, ensuring reliable and secure file transfers in "octet" mode.