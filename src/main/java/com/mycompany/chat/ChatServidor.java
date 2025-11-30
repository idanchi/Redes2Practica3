package com.mycompany.chat;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class ChatServidor {

    private static final Map<String, PrintWriter> usuarios = new ConcurrentHashMap<>();
    private static final Map<String, Socket> sockets = new ConcurrentHashMap<>();
    private static final Map<String, String> usuarioSala = new ConcurrentHashMap<>();
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

                // Solicitar nombre
                out.println("INGRESA_TU_NOMBRE");
                nombre = in.readLine();
                if (nombre == null || nombre.trim().isEmpty()) {
                    socket.close();
                    return;
                }

                // ======================================
                // VALIDAR NOMBRE DUPLICADO
                // ======================================
                while (usuarios.containsKey(nombre)) {
                    out.println("NOMBRE_INVALIDO");
                    nombre = in.readLine();
                    if (nombre == null || nombre.trim().isEmpty()) {
                        socket.close();
                        return;
                    }
                }

                // Registrar usuario
                usuarios.put(nombre, out);
                sockets.put(nombre, socket);

                out.println("Bienvenido " + nombre);
                out.println("Debes ingresar a una sala. Usa /sala <nombre> para crear/entrar o /salas para listar.");

                String msg;
                while ((msg = in.readLine()) != null) {
                    if (msg.trim().isEmpty()) continue;

                    // Archivo
                    if (msg.startsWith("/file ")) {
                        manejarEnvioArchivo(msg);
                        continue;
                    }

                    // Sala
                    if (msg.startsWith("/sala ")) {
                        String sala = msg.substring(6).trim();
                        if (!sala.isEmpty()) {
                            roomSet.add(sala);
                            usuarioSala.put(nombre, sala);
                            out.println("Ingresaste a sala '" + sala + "'");
                        }
                        continue;
                    }

                    // Listar salas
                    if (msg.equals("/salas")) {
                        out.println("SALAS " + String.join(",", roomSet));
                        continue;
                    }

                    // Usuarios
                    if (msg.equals("/usuarios")) {
                        out.println("USUARIOS " + String.join(",", usuarios.keySet()));
                        continue;
                    }

                    // Mensaje privado
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

                    // Mensaje normal → sala
                    String salaActual = usuarioSala.get(nombre);
                    if (salaActual == null) {
                        out.println("Servidor: Debes ingresar a una sala. Usa /sala <nombre>");
                        continue;
                    }

                    for (Map.Entry<String, String> e : usuarioSala.entrySet()) {
                        if (salaActual.equals(e.getValue())) {
                            PrintWriter pw = usuarios.get(e.getKey());
                            if (pw != null) {
                                pw.println(nombre + ": " + msg);
                            }
                        }
                    }
                }

            } catch (Exception e) {
                System.out.println("Cliente desconectado: " + nombre);
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

        private void manejarEnvioArchivo(String msg) throws Exception {
            String[] p = msg.split(" ");
            if (p.length < 4) {
                out.println("Servidor: Comando /file inválido.");
                return;
            }

            String destino = p[1];
            String nombreArchivo = p[2];
            long size = Long.parseLong(p[3]);

            PrintWriter outDestino = usuarios.get(destino);
            Socket socketDestino = sockets.get(destino);
            Socket socketOrigen = this.socket;

            if (outDestino == null || socketDestino == null) {
                out.println("Servidor: Usuario destino no disponible.");

                InputStream is = socketOrigen.getInputStream();
                byte[] basura = new byte[4096];
                long restante = size;

                while (restante > 0) {
                    int le = is.read(basura, 0, (int) Math.min(basura.length, restante));
                    if (le <= 0) break;
                    restante -= le;
                }
                return;
            }

            outDestino.println("/incomingFile " + nombre + " " + nombreArchivo + " " + size);

            InputStream is = socketOrigen.getInputStream();
            OutputStream os = socketDestino.getOutputStream();

            byte[] buffer = new byte[4096];
            long restante = size;

            while (restante > 0) {
                int leido = is.read(buffer, 0, (int) Math.min(buffer.length, restante));
                if (leido == -1) break;
                os.write(buffer, 0, leido);
                restante -= leido;
            }
            os.flush();
        }
    }
}
