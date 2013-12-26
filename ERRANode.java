/*
Pacchetto dati:
		1 byte con valore compreso tra 0 e 250, che indica il numero di indirizzi nella lista;
		lista di indirizzi (4 byte per ogni indirizzo);
		byte del file

Pacchetto inviato al bootstrap quando un nodo si connette alla rete:
		1 byte con valore 251  (l'indirizzo del nodo che si connette è ricavato dal socket)

Pacchetto inviato al bootstrap quando un nodo si disconnette dalla rete:
		1 byte con valore 252  (l'indirizzo del nodo che si disconnette è ricavato dal socket)

Pacchetto per richiedere la lista dei nodi al bootstrap:
		1 byte con valore 253;
		indirizzo destinazione (4 byte) (bisogna far sapere al bootstrap l'indirizzo di destinazione perché è quello che va 
										 messo alla fine della lista)

Pacchetto di risposta dal bootstrap con la lista di indirizzi in ordine casuale:
		1 byte con valore 254;
		1 byte che indica il numero di indirizzi nella lista; (non sarebbe strettamente necessario ma semplifica il codice, 
															   perché il nodo sorgente può inoltrare direttamente la lista al 
															   prossimo nodo senza prima leggerla tutta per sapere quanto è lunga)
		lista di indirizzi

Pacchetto di risposta dal bootstrap che indica che il nodo destinazione richiesto non esiste nella rete:
		1 byte con valore 255

*/


/*
Il programma può essere fatto partire con
	java ERRANode <indirizzo bootstrap>
oppure
	java ERRANode <indirizzo bootstrap> <file da inviare>
*/

import java.net.*;
import java.io.*;

public class ERRANode {

	private static final int CONNECTION_CODE = 251;
	private static final int DISCONNECTION_CODE = 252;
	private static final int ADDRESSES_LIST_REQUEST_CODE = 253;
	private static final int BOOTSTRAP_RESPONSE_SUCCESS_CODE = 254;
	private static final int BOOTSTRAP_RESPONSE_ERROR_CODE = 255;

	private String bootstrapAddress;

	public static void main(String[] args) {
            
        	
        	if (args.length == 0)
                        System.err.println("Specify the bootstrap address");
                else if (args.length == 1) {
                        ERRANode node = new ERRANode(args[0]);
                        node.waitForMessage();
                		}
                	else {
                		ERRANode node = new ERRANode(args[0], args[1]);
                		node.waitForMessage();
                	}
        }

	public ERRANode(String bootstrapAddress) {
		this.bootstrapAddress = bootstrapAddress;
		joinNetwork();
		createServer();
	}

	public ERRANode(String bootstrapAddress, String fileName) {
		this.bootstrapAddress = bootstrapAddress;
		joinNetwork();
		sendFile(fileName);
		createServer();
	}

	private void waitForMessage() {
    	String name ="";
    	Scanner in = new Scanner(System.in);
    	
    	while (!(name.equals("/logout"))) {
    		System.out.print("Nome file da mandare (/logout per disconnettersi): ");
    		name = in.nextLine();
    		sendFile(name);
    	}
    	
    	in.close();
    	disconnectFromNetwork();
    	
    	}	
	
	private void joinNetwork() {
		Socket bootstrapSocket = null;
		try {
			bootstrapSocket = new Socket(bootstrapAddress, 10000);
			DataOutputStream output = new DataOutputStream(bootstrapSocket.getOutputStream());
			output.writeByte(CONNECTION_CODE);
		} catch (IOException e) {
			System.err.println("Problem connecting to the ERRA network");
		} finally {    // i socket vanno chiusi sia che ci sia un errore sia che non ci sia, quindi la chiusura va nel finally
			try {
				if (bootstrapSocket != null)
					bootstrapSocket.close();
			} catch (IOException e) {

			}
		}
	}

	private void disconnectFromNetwork() {
		Socket bootstrapSocket = null;
		try {
			bootstrapSocket = new Socket(bootstrapAddress, 10000);
			DataOutputStream output = new DataOutputStream(bootstrapSocket.getOutputStream());
			output.writeByte(DISCONNECTION_CODE);
		} catch (IOException e) {
			System.err.println("Problem disconnecting from the ERRA network");
		} finally {
			try {
				if (bootstrapSocket != null)
					bootstrapSocket.close();
			} catch (IOException e) {

			}
		}
	}

	private void createServer() {
		ServerSocket server = null;
		try {
			server = new ServerSocket(10000);
		} catch (IOException e) {
			System.err.println("Problem creating this node's server\n" + e.toString());
		}

		while (true) {
			System.out.println("Waiting for packets");
			Socket inputSocket = null;
			try {
				inputSocket = server.accept();
				DataInputStream input = new DataInputStream(inputSocket.getInputStream());
				System.out.println("A new packet has arrived");
				forwardPacket(input);
			} catch (IOException e) {

			} finally {
				try {
					inputSocket.close();
				} catch (IOException e) {

				}
			}
		}
	}

	private void sendFile(String fileName) {
		Socket bootstrapSocket = null;
		Socket outputSocket = null;
		try {
			// Richiesta della lista di indirizzi al bootstrap
			bootstrapSocket = new Socket(bootstrapAddress, 10000);
			DataOutputStream bootstrapOutput = new DataOutputStream(bootstrapSocket.getOutputStream());
			bootstrapOutput.writeByte(ADDRESSES_LIST_REQUEST_CODE);
			InetAddress destination = InetAddress.getByName(fileName);
			bootstrapOutput.write(destination.getAddress(), 0, 4);
			
			DataInputStream input = new DataInputStream(bootstrapSocket.getInputStream());
			byte firstByte = input.readByte();
			if ((firstByte & 0xFF) == BOOTSTRAP_RESPONSE_ERROR_CODE) {
				System.out.println("The specified destination is not connected to the ERRA network");
				return;
			}

			// Invio dell'header del pacchetto al prossimo nodo
			byte remainingNodesNum = input.readByte();
			byte[] nextHopAddress = new byte[4];
			input.read(nextHopAddress);

			outputSocket = new Socket(InetAddress.getByAddress(nextHopAddress), 10000);
			DataOutputStream output = new DataOutputStream(outputSocket.getOutputStream());

			output.writeByte(remainingNodesNum - 1);
			byte[] buffer = new byte[1024];     // 1024 è un numero a caso, questa parte si può migliorare
			int len;
			while ((len = input.read(buffer)) > 0)
				output.write(buffer, 0, len);

			// Invio del file al prossimo nodo
			FileInputStream fis = new FileInputStream(new File(fileName));
			while ((len = fis.read(buffer)) > 0)
				output.write(buffer, 0, len);

			outputSocket.close();
		} catch (IOException e) {
			System.err.println("Problem sending file " + e.toString());
			return;
		} finally {
			try {
				if (bootstrapSocket != null)
					bootstrapSocket.close();
				if (outputSocket != null)
					outputSocket.close();
			} catch (IOException e) {

			}
		}
	}

	private void forwardPacket(DataInputStream input) throws IOException {
		byte remainingNodesNum = input.readByte();
		System.out.println("This packets's remaining nodes: " + (remainingNodesNum & 0xFF));
		if (remainingNodesNum == 0) {
			System.out.println("Packet reached the destination");
			return;
		}

		byte[] nextHopAddress = new byte[4];
		input.read(nextHopAddress);

		Socket outputSocket = new Socket(InetAddress.getByAddress(nextHopAddress), 10000);
		DataOutputStream output = new DataOutputStream(outputSocket.getOutputStream());

		output.writeByte(remainingNodesNum - 1);
		byte[] buffer = new byte[1024];    // 1024 è un numero a caso, questa parte si può migliorare
		int len;
		while ((len = input.read(buffer)) > 0)
			output.write(buffer, 0, len);

		outputSocket.close();
		System.out.println("Packet forwarded");
	}

}
