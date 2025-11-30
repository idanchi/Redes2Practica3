package com.mycompany.chat;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Server.java - servidor simple con salas que se crean automáticamente
 * y transferencia de archivos.
 */
public class ChatServidor {

    private static final Map<String, PrintWriter> usuarios = new ConcurrentHashMap<>();
    private static final Map<String, Socket> sockets = new ConcurrentHashMap<>();
    // map user -> sala actual
    private static final Map<String, String> usuarioSala = new ConcurrentHashMap<>();
    // conjunto de salas existentes
    private static final Set<String> roomSet = ConcurrentHashMap.newKeySet();

    public static void main(String[] args) throws Exception {
        ServerSocket server = new ServerSocket(5000);
        System.out.println("Servidor iniciado en puerto 5000...");

        while (true) {
            Socket socket = server.accept();
            new Thread(new ManejadorCliente(socket)).start();
        }
    }

    static class ManejadorCliente implements Runnable {
        private Socket socket;
        private PrintWriter out;
        private BufferedReader in;
        private String nombre;

        ManejadorCliente(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                out = new PrintWriter(socket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                // Pedir nombre (protocolo compacto)
                out.println("INGRESA_TU_NOMBRE");
                nombre = in.readLine();
                if (nombre == null || nombre.trim().isEmpty()) {
                    socket.close();
                    return;
                }

                usuarios.put(nombre, out);
                sockets.put(nombre, socket);

                out.println("Bienvenido " + nombre);
                out.println("Debes ingresar a una sala. Usa /sala <nombre> para crear/entrar o /salas para listar.");

                String msg;
                while ((msg = in.readLine()) != null) {
                    if (msg.trim().isEmpty()) continue;

                    // Comando transferir archivo
                    if (msg.startsWith("/file ")) {
                        manejarEnvioArchivo(msg);
                        continue;
                    }

                    // Comando crear/entrar sala (ahora crea si no existe)
                    if (msg.startsWith("/sala ")) {
                        String sala = msg.substring(6).trim();
                        if (sala.isEmpty()) {
                            out.println("Servidor: Nombre de sala inválido.");
                            continue;
                        }
                        // Crear sala si no existe
                        roomSet.add(sala);

                        // Remover usuario de su sala anterior (si aplica)
                        usuarioSala.put(nombre, sala);

                        out.println("Ingresaste a sala '" + sala + "'");
                        continue;
                    }

                    // Listar salas
                    if (msg.equals("/salas")) {
                        String lista = String.join(",", roomSet);
                        out.println("SALAS " + lista);
                        continue;
                    }

                    // Mensaje privado (/priv <dest> <texto>)
                    if (msg.startsWith("/priv ") || msg.startsWith("/privado ")) {
                        String[] p = msg.split(" ", 3);
                        if (p.length >= 3) {
                            String destino = p[1];
                            String texto = p[2];
                            PrintWriter outDest = usuarios.get(destino);
                            if (outDest != null) {
                                outDest.println("[Privado] " + nombre + ": " + texto);
                            } else {
                                out.println("Servidor: Usuario '" + destino + "' no encontrado.");
                            }
                        }
                        continue;
                    }

                    // Listar usuarios (/usuarios)
                    if (msg.equals("/usuarios")) {
                        out.println("USUARIOS " + String.join(",", usuarios.keySet()));
                        continue;
                    }

                    // Mensaje normal -> enviar a la sala actual del remitente
                    String salaActual = usuarioSala.get(nombre);
                    if (salaActual == null) {
                        out.println("Servidor: Debes ingresar a una sala. Usa /sala <nombre>");
                        continue;
                    }

                    // enviar a todos los usuarios en la misma sala
                    for (Map.Entry<String, String> e : usuarioSala.entrySet()) {
                        if (salaActual.equals(e.getValue())) {
                            PrintWriter pw = usuarios.get(e.getKey());
                            if (pw != null) pw.println(nombre + ": " + msg);
                        }
                    }
                }

            } catch (Exception e) {
                System.out.println("Cliente desconectado: " + nombre + " (" + e.getMessage() + ")");
            } finally {
                try {
                    if (nombre != null) {
                        usuarios.remove(nombre);
                        sockets.remove(nombre);
                        usuarioSala.remove(nombre);
                    }
                    socket.close();
                } catch (Exception ex) {}
            }
        }

        // ===========================
        // TRANSFERENCIA DE ARCHIVOS
        // Protocolo: cliente emisor manda una línea:
        // /file <destino> <nombreArchivo> <tamaño>
        // luego envía exactamente <tamaño> bytes por el socket.
        // El servidor reenvía esos bytes al socket del destino.
        // ===========================
        private void manejarEnvioArchivo(String msg) throws Exception {
            String[] p = msg.split(" ");
            if (p.length < 4) {
                out.println("Servidor: Comando /file inválido.");
                return;
            }
            String destino = p[1];
            String nombreArchivo = p[2];
            long size;
            try {
                size = Long.parseLong(p[3]);
            } catch (NumberFormatException ex) {
                out.println("Servidor: tamaño inválido.");
                return;
            }

            PrintWriter outDestino = usuarios.get(destino);
            Socket socketDestino = sockets.get(destino);
            Socket socketOrigen = this.socket;

            if (outDestino == null || socketDestino == null) {
                out.println("Servidor: Usuario destino no disponible.");
                // consumir bytes del remitente para no dejar el stream en mal estado
                InputStream is = socketOrigen.getInputStream();
                byte[] basura = new byte[4096];
                long restante = size;
                while (restante > 0) {
                    int le = is.read(basura, 0, (int)Math.min(basura.length, restante));
                    if (le <= 0) break;
                    restante -= le;
                }
                return;
            }

            // avisar al receptor
            outDestino.println("/incomingFile " + nombre + " " + nombreArchivo + " " + size);

            InputStream is = socketOrigen.getInputStream();
            OutputStream os = socketDestino.getOutputStream();

            byte[] buffer = new byte[4096];
            long restante = size;

            while (restante > 0) {
                int leido = is.read(buffer, 0, (int)Math.min(buffer.length, restante));
                if (leido == -1) break;
                os.write(buffer, 0, leido);
                restante -= leido;
            }
            os.flush();
        }
    }
}
