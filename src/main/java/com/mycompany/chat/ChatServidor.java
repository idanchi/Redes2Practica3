package com.mycompany.chat;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class ChatServidor {
    private static final int PUERTO = 12345;
    // ConcurrentHashMap es importante para ser seguro en hilos
    private static final Map<String, PrintWriter> usuarios = new ConcurrentHashMap<>(); 

    public static void main(String[] args) {
        System.out.println("Servidor de chat iniciado en el puerto " + PUERTO);

        try (ServerSocket serverSocket = new ServerSocket(PUERTO)) {
            while (true) {
                Socket cliente = serverSocket.accept();
                // Cada conexión de cliente se maneja en un hilo separado
                new Thread(new ManejadorCliente(cliente)).start(); 
            }
        } catch (IOException e) {
            System.err.println("Error en el servidor: " + e.getMessage());
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

                // Solicitar nombre de usuario
                salida.println("INGRESA_TU_NOMBRE");
                nombreUsuario = entrada.readLine();

                if (nombreUsuario == null || nombreUsuario.isEmpty() || usuarios.containsKey(nombreUsuario)) {
                    salida.println("NOMBRE_INVALIDO");
                    socket.close();
                    return;
                }

                // Registrar usuario
                usuarios.put(nombreUsuario, salida);
                broadcast("Servidor", nombreUsuario + " se ha unido al chat.");

                String mensaje;
                while ((mensaje = entrada.readLine()) != null) {
                    if (mensaje.startsWith("/privado ")) {
                        manejarMensajePrivado(mensaje);
                    } else if (mensaje.equalsIgnoreCase("/usuarios")) { // <-- Comando Listar
                        listarUsuarios(); // <-- Llamada al nuevo método
                    } else if (mensaje.startsWith("/ingresar")) {
                        // Comando interno del cliente
                    } else {
                        broadcast(nombreUsuario, mensaje);
                    }
                }
            } catch (IOException e) {
                // Manejar desconexión
                System.err.println("Error con usuario " + nombreUsuario + ": " + e.getMessage());
            } finally {
                // Limpieza final
                if (nombreUsuario != null) {
                    usuarios.remove(nombreUsuario);
                    broadcast("Servidor", nombreUsuario + " ha salido del chat.");
                }

                try {
                    socket.close();
                } catch (IOException e) {
                    System.err.println("Error al cerrar socket de " + nombreUsuario);
                }
            }
        }
        
        // **NUEVO MÉTODO**
        private void listarUsuarios() {
            Set<String> listaNombres = usuarios.keySet();
            
            // Construir la cadena con los nombres separados por comas
            String usuariosActivos = String.join(", ", listaNombres);

            // Enviar la lista de vuelta SOLO al cliente que la solicitó
            salida.println("Servidor: Usuarios activos (" + listaNombres.size() + "): " + usuariosActivos);
        }
        
        // Método ya existente para mensaje privado
        private void manejarMensajePrivado(String mensaje) {
            try {
                String[] partes = mensaje.split(" ", 3);
                if (partes.length < 3) return;

                String destinatario = partes[1];
                String mensajePrivado = partes[2];

                PrintWriter salidaDestino = usuarios.get(destinatario);
                if (salidaDestino != null) {
                    salidaDestino.println("[Privado] " + nombreUsuario + ": " + mensajePrivado);
                } else {
                    salida.println("Servidor: Usuario '" + destinatario + "' no encontrado.");
                }
            } catch (Exception e) {
                salida.println("Servidor: Error al enviar mensaje privado.");
            }
        }

        // Método ya existente para difundir
        private void broadcast(String remitente, String mensaje) {
            for (PrintWriter escritor : usuarios.values()) {
                escritor.println(remitente + ": " + mensaje);
            }
        }
    }
}