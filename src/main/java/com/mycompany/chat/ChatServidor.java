package com.mycompany.chat;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class ChatServidor {
    private static final int PUERTO = 12345;

    private static final Map<String, PrintWriter> usuarios = new ConcurrentHashMap<>();
    private static final Map<String, Set<String>> salas = new ConcurrentHashMap<>();
    private static final Map<String, String> salaDeUsuario = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        System.out.println("Servidor iniciado en puerto " + PUERTO);

        try (ServerSocket serverSocket = new ServerSocket(PUERTO)) {
            while (true) {
                Socket cliente = serverSocket.accept();
                new Thread(new ManejadorCliente(cliente)).start();
            }
        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
        }
    }

    private static class ManejadorCliente implements Runnable {
        private Socket socket;
        private String nombreUsuario;
        private BufferedReader entrada;
        private PrintWriter salida;

        public ManejadorCliente(Socket socket) {
            this.socket = socket;
        }

        public void run() {
            try {
                entrada = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                salida = new PrintWriter(socket.getOutputStream(), true);

                salida.println("INGRESA_TU_NOMBRE");
                nombreUsuario = entrada.readLine();

                if (nombreUsuario == null || nombreUsuario.trim().isEmpty() || usuarios.containsKey(nombreUsuario)) {
                    salida.println("NOMBRE_INVALIDO");
                    socket.close();
                    return;
                }

                usuarios.put(nombreUsuario, salida);

                salida.println("Servidor: Bienvenido " + nombreUsuario);

                String mensaje;
                while ((mensaje = entrada.readLine()) != null) {

                    if (mensaje.startsWith("/crear ")) {
                        crearSala(mensaje.substring(7).trim());
                    } else if (mensaje.equals("/salas")) {
                        listarSalas();
                    } else if (mensaje.startsWith("/ingresar ")) {
                        ingresarSala(mensaje.substring(10).trim());
                    } else if (mensaje.equals("/usuarios")) {
                        listarUsuarios();
                    } else if (mensaje.startsWith("/privado ")) {
                        manejarPrivado(mensaje);
                    } else {
                        enviarASalaActual(mensaje);
                    }
                }

            } catch (Exception e) {
                System.err.println("Desconexión: " + nombreUsuario);
            } finally {
                usuarios.remove(nombreUsuario);
                String sala = salaDeUsuario.get(nombreUsuario);
                if (sala != null) salas.get(sala).remove(nombreUsuario);
                broadcastGlobal("Servidor", nombreUsuario + " ha salido.");
                try { socket.close(); } catch (IOException e) {}
            }
        }

        private void crearSala(String sala) {
            if (!salas.containsKey(sala)) {
                salas.put(sala, ConcurrentHashMap.newKeySet());
            }
            ingresarSala(sala);
        }

        private void ingresarSala(String sala) {
            if (!salas.containsKey(sala)) {
                salida.println("Servidor: La sala no existe. Usa /crear nombre");
                return;
            }

            salas.forEach((s, set) -> set.remove(nombreUsuario));
            salas.get(sala).add(nombreUsuario);
            salaDeUsuario.put(nombreUsuario, sala);

            salida.println("Servidor: Ingresaste a sala '" + sala + "'");
        }

        private void listarSalas() {
            salida.println("SALAS " + String.join(",", salas.keySet()));
        }

        private void listarUsuarios() {
            salida.println("USUARIOS " + String.join(",", usuarios.keySet()));
        }

        private void manejarPrivado(String msg) {
            String[] p = msg.split(" ", 3);
            if (p.length < 3) return;

            PrintWriter dest = usuarios.get(p[1]);
            if (dest != null)
                dest.println("[Privado] " + nombreUsuario + ": " + p[2]);
            else
                salida.println("Servidor: Usuario no encontrado.");
        }

        private void enviarASalaActual(String mensaje) {
            String sala = salaDeUsuario.get(nombreUsuario);
            if (sala == null) {
                salida.println("Servidor: Debes ingresar a una sala.");
                return;
            }

            for (String u : salas.get(sala)) {
                usuarios.get(u).println(nombreUsuario + ": " + mensaje);
            }
        }

        private void broadcastGlobal(String remitente, String mensaje) {
            for (PrintWriter pw : usuarios.values()) {
                pw.println(remitente + ": " + mensaje);
            }
        }
    }
}
