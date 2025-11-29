package com.mycompany.chat;

import java.io.*;
import java.net.*;
import javax.swing.*;

public class FileTransferHandler {
    private static final int BUFFER_SIZE = 4096;
    private static final int UDP_PORT = 54321;
    
    public static void sendFile(File file, String destinationIP, ProgressListener progressListener) {
        new Thread(() -> {
            try (DatagramSocket socket = new DatagramSocket();
                 FileInputStream fis = new FileInputStream(file)) {
                
                InetAddress address = InetAddress.getByName(destinationIP);
                byte[] buffer = new byte[BUFFER_SIZE];
                long fileSize = file.length();
                long totalRead = 0;
                int read;
                
                // Enviar metadatos
                String metadata = file.getName() + ":" + fileSize;
                byte[] metaBytes = metadata.getBytes();
                DatagramPacket metaPacket = new DatagramPacket(metaBytes, metaBytes.length, address, UDP_PORT);
                socket.send(metaPacket);
                
                // Enviar archivo en chunks
                while ((read = fis.read(buffer)) != -1) {
                    DatagramPacket packet = new DatagramPacket(buffer, read, address, UDP_PORT);
                    socket.send(packet);
                    totalRead += read;
                    
                    if (progressListener != null) {
                        int progress = (int) ((totalRead * 100) / fileSize);
                        progressListener.onProgressUpdate(progress);
                    }
                    
                    Thread.sleep(10);
                }
                
                if (progressListener != null) {
                    progressListener.onTransferComplete(true);
                }
                
            } catch (Exception e) {
                if (progressListener != null) {
                    progressListener.onTransferComplete(false);
                }
                JOptionPane.showMessageDialog(null, "Error al enviar archivo: " + e.getMessage());
            }
        }).start();
    }
    
    public static void startFileReceiver(File saveDirectory, FileReceivedListener messageListener) {
        new Thread(() -> {
            try (DatagramSocket socket = new DatagramSocket(UDP_PORT)) {
                byte[] buffer = new byte[BUFFER_SIZE];
                
                while (true) {
                    // Recibir metadatos
                    DatagramPacket metaPacket = new DatagramPacket(buffer, buffer.length);
                    socket.receive(metaPacket);
                    String metadata = new String(metaPacket.getData(), 0, metaPacket.getLength());
                    String[] parts = metadata.split(":");
                    String fileName = parts[0];
                    long fileSize = Long.parseLong(parts[1]);
                    
                    // Preparar archivo de destino
                    File outputFile = new File(saveDirectory, fileName);
                    try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                        long totalReceived = 0;
                        
                        while (totalReceived < fileSize) {
                            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                            socket.receive(packet);
                            int bytesReceived = packet.getLength();
                            fos.write(packet.getData(), 0, bytesReceived);
                            totalReceived += bytesReceived;
                        }
                        
                        if (messageListener != null) {
                            messageListener.onFileReceived(outputFile);
                        }
                    }
                }
            } catch (Exception e) {
                JOptionPane.showMessageDialog(null, "Error en receptor de archivos: " + e.getMessage());
            }
        }).start();
    }
    
    public interface ProgressListener {
        void onProgressUpdate(int progress);
        void onTransferComplete(boolean success);
    }
    
    public interface FileReceivedListener {
        void onFileReceived(File file);
    }
}