import java.io.FileNotFoundException;
import java.io.InputStreamReader;
import java.io.FileInputStream;
import java.io.BufferedReader;
import java.io.OutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.FileReader;
import java.io.File;
import java.net.ServerSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Map;
import java.util.Date;
import java.util.HashMap;

public class HttpServer {
    private static class Processor implements Runnable {
        private Socket socket;
        private OutputStream output;
        private BufferedReader reader;
        private String root;
        
        private Processor(Socket socket, String root) throws IOException {
            this.root = root;
            this.socket = socket;
            this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        }
        
        private byte[] byteStream(String fileRequest) {
            File file = new File(root + fileRequest);
            byte[] byteStream = new byte[(int)file.length()]; //???
            FileInputStream fileInputStream = null;
            String error405 = "/405.html";
            String error404 = "/404.html";
            
            try {
                fileInputStream = new FileInputStream(file);
                fileInputStream.read(byteStream);

                if ( error405.equals(fileRequest) ) {
                    System.out.println("405 /");
                } else if ( error404.equals(fileRequest) ) {
                    System.out.println("404 /");
                } else {
                    System.out.println("200 " + fileRequest);
                }
            } catch ( FileNotFoundException ex ) {
                if ( fileRequest.equals(error404) ) {
                    System.out.println("File 404 not found.");
                } else {
                    byteStream = byteStream(error404);
                }
            } catch ( IOException ex ) {
                System.err.println("Error Reading the file.");
            } finally {
                try {
                    if ( fileInputStream != null ) {
                        fileInputStream.close();
                    }
                } catch ( IOException ex ) {}
            }
            return byteStream;
        }

        private String getResponse(String mapValue, int length) {
            StringBuffer response = new StringBuffer();

            if ( mapValue == null ) {
                mapValue = "application/octet-stream";
            }

            response.append("HTTP/1.1 200 OK\r\n");
            response.append("Connection: close\r\n");
            response.append("Content-Length: " + length + "\r\n");
            response.append("Content-Type: " + mapValue +  "\r\n");
            response.append("Date: " + new Date() +  "\r\n\r\n");
            return response.toString();
        }
        
        private void writeResponse(String file) throws IOException {
            OutputStream output = socket.getOutputStream();
            byte[] text = byteStream(file);
            Map<String, String> mimeMap = parseFile("mime.types");
            String key = file.split("\\.")[1];
            String mapValue = mimeMap.get(key);
            String response = getResponse(mapValue, text.length);
            byte[] result = new byte[text.length + response.getBytes().length];
            System.arraycopy(response.getBytes(), 0, result, 0, response.getBytes().length);
            System.arraycopy(text, 0, result, response.getBytes().length, text.length);
            
            output.write(result);
            output.flush();
        }
        
        public static Map<String, String> parseFile(String fileName) {
            Map<String, String> map = new HashMap<String, String>();
            BufferedReader br = null;
            String line;

            try {
                br = new BufferedReader(new FileReader(fileName));

                while ( (line = br.readLine()) != null ) {
                    String[] parts = line.split("\\s", 2);
                    int last = parts.length - 1;

                    if ( parts[0].equals("")) {} 
                    else {
                        parts[last] = parts[last].trim();
                        map.put(parts[0], parts[last]);
                    }
                }
            } catch ( FileNotFoundException ex ) {
                ex.printStackTrace();
            } catch ( IOException ex ) {
                ex.printStackTrace();
            }
            return map;
        }
        
        public void run() {
            try {
                String line = reader.readLine();
                String[] configArray = line.split(" ");

                if ( !configArray[0].equals("GET") ) {
                    writeResponse("/405.html");
                } else if ( configArray[1].equals("/") ) {
                    writeResponse("/index.html");
                } else {
                    writeResponse(configArray[1]);
                }
            } catch ( IOException ex ) {
            } finally {
                try {
                    socket.close();
                } catch ( IOException ex ) {
                }
            }
        }
    }

    public static void main(String[] args) throws IOException {
        Map<String, String> httpMap = Processor.parseFile("http.conf");
        int port = Integer.parseInt(httpMap.get("port"));
        String address = httpMap.get("address");
        String root = httpMap.get("root_dir");
        int backlog = 42;
        ServerSocket server = new ServerSocket(port, backlog, InetAddress.getByName(address));
        
        while (true) {
            Socket socket = server.accept();
            new Thread(new Processor(socket, root)).start();
        }
    }
}
