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
		lista di indirizzi

Pacchetto di risposta dal bootstrap che indica che il nodo destinazione richiesto non esiste nella rete:
		1 byte con valore 255

*/


// Il programma viene fatto partire con "java ERRANode <indirizzo bootstrap>"

import java.net.*;
import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;
import java.util.Scanner;

public class ERRANode {

	private static final int CONNECTION_CODE = 251;
	private static final int DISCONNECTION_CODE = 252;
	private static final int ADDRESSES_LIST_REQUEST_CODE = 253;
	private static final int BOOTSTRAP_RESPONSE_SUCCESS_CODE = 254;
	private static final int BOOTSTRAP_RESPONSE_ERROR_CODE = 255;

	private static String bootstrapAddress;

	public static void main(String[] args) {          
        	
		if (args.length == 0)
			System.err.println("Specify the bootstrap address");
		else {
			bootstrapAddress = args[0];
			joinNetwork();
			new PacketListener().start();
			waitForMessage();
		}
	}

	private static void waitForMessage() {
		String name = "";
		Scanner in = new Scanner(System.in);

		while (!(name.equals("/logout"))) {
			System.out.print("Nome file da mandare (/logout per disconnettersi): ");
			name = in.nextLine();
			sendFile(name);
		}

		in.close();
		disconnectFromNetwork();
	}
	
	private static void joinNetwork() {
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
				e.printStackTrace();
			}
		}
	}

	private static void disconnectFromNetwork() {
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
				e.printStackTrace();
			}
		}
	}

	private static void sendFile(String fileName) {
		Socket outputSocket = null;
		try {
			InetAddress destination = InetAddress.getByName(fileName);
			ArrayList<InetAddress> addressesList = requestAddressesList(destination);
			if (addressesList == null) {
				System.out.println("The specified destination is not connected to the ERRA network");
				return;
			}
			
			/////////////////////////////////////////////////////////////////////////////////////
			File[] pktMSG = new File[10]; //da rivedere anche in base al merge e alla dim di msg
			File f = new File(fileName);
			BufferedInputStream bis = new BufferedInputStream(new FileInputStream(f));
			FileOutputStream out;
			String name = f.getName();
			int partCounter = 0;
			int sizeOfFiles = 1500;// questo è da stabilire vediamo
			byte[] buffer = new byte[sizeOfFiles];
			int tmp = 0;
			while ((tmp = bis.read(buffer)) > 0) {
			 File newFile=new File(fileName+"."+String.format("%03d", partCounter));
			 newFile.createNewFile();
			 out = new FileOutputStream(newFile);
			 out.write(buffer,0,tmp);
			 pktMSG[partCounter] = newFile;
			 partCounter++;
			 out.close();
			}
			
			//////////////per ogni file di pktMSG://///////////////////////777////
			//for (int i=0 to partCounter -1) {
			////////////////////////////////////////////////////////////////////
			
			// quando divideremo i file in pezzi tutto il codice qua sotto andrà ripetuto per ogni pezzo 
			Collections.shuffle(addressesList, new Random(System.nanoTime()));
			InetAddress nextNode = addressesList.remove(addressesList.size() - 1);
			addressesList.add(destination);   // mette in fondo alla lista l'indirizzo della destinazione

			outputSocket = new Socket(nextNode, 10000);
			DataOutputStream output = new DataOutputStream(outputSocket.getOutputStream());

			output.writeByte(addressesList.size());
			for (InetAddress nodeAddress : addressesList)
				output.write(nodeAddress.getAddress(), 0, 4);   // ogni indirizzo viene mandato come gruppo di 4 byte

			// invio del file al prossimo nodo
			byte[] buffer = new byte[1024];     // 1024 è un numero a caso, questa parte si può migliorare
			int len;
			FileInputStream fis = new FileInputStream(new File(fileName));
			while ((len = fis.read(buffer)) > 0)
				output.write(buffer, 0, len);
		} catch (IOException e) {
			System.err.println("Problem sending file");
			return;
		} finally {
			try {
				if (outputSocket != null)
					outputSocket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private static ArrayList<InetAddress> requestAddressesList(InetAddress destination) {
		Socket bootstrapSocket = null;
		ArrayList<InetAddress> addressesList = new ArrayList<InetAddress>();
		try {
			bootstrapSocket = new Socket(bootstrapAddress, 10000);
			DataOutputStream bootstrapOutput = new DataOutputStream(bootstrapSocket.getOutputStream());
			bootstrapOutput.writeByte(ADDRESSES_LIST_REQUEST_CODE);
			bootstrapOutput.write(destination.getAddress(), 0, 4);
			
			DataInputStream input = new DataInputStream(bootstrapSocket.getInputStream());
			byte firstByte = input.readByte();
			if ((firstByte & 0xFF) == BOOTSTRAP_RESPONSE_ERROR_CODE)
				return null;  // la destinazione non esiste

			byte[] buffer = new byte[4];
			int len;
			while ((len = input.read(buffer)) > 0) {
				addressesList.add(InetAddress.getByAddress(buffer));
			}
		} catch (IOException e) {
			System.err.println("Problem requesting addresses list to bootstrap");
		} finally {
			try {
				if (bootstrapSocket != null)
					bootstrapSocket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return addressesList;
	}

	private static class PacketListener extends Thread {

		public void run() {
			ServerSocket server = null;
			try {
				server = new ServerSocket(10000);
			} catch (IOException e) {
				System.err.println("Problem creating this node's server");
			}

			while (true) {
				System.out.println("Waiting for packets");
				Socket inputSocket = null;
				try {
					inputSocket = server.accept();
					System.out.println("A new packet has arrived");

					DataInputStream input = new DataInputStream(inputSocket.getInputStream());
					byte firstByte = input.readByte();
					if (firstByte == 0) {
						System.out.println("Packet reached the destination");
						//ho aggiunto sto pezzo perchè mi serve passare al metodo
						//il nome del file
						String name = "";
						name = inputSocket.getLocalAddress().getHostName();
						
						
						saveFileFragment(input, name);
					} else {
						forwardPacket(input, firstByte);
					}
				} catch (IOException e) {
					e.printStackTrace();
				} finally {
					try {
						inputSocket.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
		}

		private void forwardPacket(DataInputStream input, byte remainingNodesNum) {
			try {
				System.out.println("This packet's remaining nodes: " + (remainingNodesNum & 0xFF));

				byte[] nextHopAddress = new byte[4];
				input.read(nextHopAddress);
				System.out.println(InetAddress.getByAddress(nextHopAddress));
				Socket outputSocket = new Socket(InetAddress.getByAddress(nextHopAddress), 10000);
				DataOutputStream output = new DataOutputStream(outputSocket.getOutputStream());

				output.writeByte(remainingNodesNum - 1);
				byte[] buffer = new byte[1024];    // 1024 è un numero a caso, questa parte si può migliorare
				int len;
				while ((len = input.read(buffer)) > 0)
					output.write(buffer, 0, len);

				outputSocket.close();
				System.out.println("Packet forwarded");
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		private void saveFileFragment(DataInputStream input, String name) {
			FileOutputStream fos = null;
			try {
				fos = new FileOutputStream("/home/ERRA/" + name);
				byte[] buf = new byte[1500]; //qui dipende da quanto facciamo i sottomessaggi
				int i = 0;
				// riga per riga leggo il file originale per 
				// scriverlo nello stram del file destinazione
				while ((i=input.read(buf)) != -1) {
					fos.write(buf, 0, i);
				}

				System.out.println("file salvato in /home/ERRA/" + name);
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				try {
					fos.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}

	}

}
