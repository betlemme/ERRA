// Il programma viene fatto partire con "java ERRABootstrapNode"

import java.net.*;
import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;

public class ERRABootstrapNode {

	private static final int CONNECTION_CODE = 251;
	private static final int DISCONNECTION_CODE = 252;
	private static final int ADDRESSES_LIST_REQUEST_CODE = 253;
	private static final int BOOTSTRAP_RESPONSE_SUCCESS_CODE = 254;
	private static final int BOOTSTRAP_RESPONSE_ERROR_CODE = 255;

	private ArrayList<InetAddress> addressesList;

	public static void main(String[] args) {
		new ERRABootstrapNode();
	}

	public ERRABootstrapNode() {
		addressesList = new ArrayList<InetAddress>();
		createNode();
	}

	private void createNode() {
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
				DataInputStream input = new DataInputStream(inputSocket.getInputStream());
				System.out.println("A new packet has arrived");

				byte firstByte = input.readByte();
				switch (firstByte & 0xFF) {      // conversione a byte senza segno
					case CONNECTION_CODE:
						addNewNode(inputSocket);
						break;
					case DISCONNECTION_CODE:
						removeNode(inputSocket);
						break;
					case ADDRESSES_LIST_REQUEST_CODE:
						sendAddressesList(inputSocket);
						break;
					case BOOTSTRAP_RESPONSE_SUCCESS_CODE:      // questo caso si può anche togliere, l'ho messo per debug
						System.err.println("This should never happen");  
						break;
					case BOOTSTRAP_RESPONSE_ERROR_CODE:        // questo caso si può anche togliere, l'ho messo per debug
						System.err.println("This should never happen");  
						break;
					default:
						forwardPacket(input, firstByte);
						break;
				}
			} catch (IOException e) {

			} finally {
				try {
					inputSocket.close();
				} catch (IOException e) {

				}
			}
		}
	}

	private void sendFile() {
		// TODO
	}

	private void forwardPacket(DataInputStream input, byte remainingNodesNum) throws IOException {
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
		byte[] buffer = new byte[1024];
		int len;
		while ((len = input.read(buffer)) > 0)
			output.write(buffer, 0, len);

		outputSocket.close();
		System.out.println("Packet forwarded");
	}

	private void addNewNode(Socket inputSocket) throws IOException {
		InetAddress newNodeAddress = inputSocket.getInetAddress();
		if (addressesList.isEmpty())
			addressesList.add(inputSocket.getLocalAddress());  // il primo nodo che si connette alla rete permette al bootstrap
															   // di conoscere il proprio indirizzo IP, che viene ricavato
															   // dal socket della connessione
		addressesList.add(newNodeAddress);
		System.out.println("Node with address " +  newNodeAddress.getHostAddress() + " added to the network");
	}

	private void removeNode(Socket inputSocket) throws IOException {
		InetAddress removedNodeAddress = inputSocket.getInetAddress();
		addressesList.remove(removedNodeAddress);
		System.out.println("Node with address " +  removedNodeAddress.getHostAddress() + " removed from the network");
	}

	private void sendAddressesList(Socket inputSocket) throws IOException {
		byte[] destinationAddress = new byte[4];
		DataInputStream input = new DataInputStream(inputSocket.getInputStream());
		input.read(destinationAddress);
		InetAddress destination = InetAddress.getByAddress(destinationAddress);
		InetAddress source = inputSocket.getInetAddress();

		Collections.shuffle(addressesList, new Random(System.nanoTime()));
		
		System.out.println("Sending addresses list to node " + source.getHostAddress());
		DataOutputStream output = new DataOutputStream(inputSocket.getOutputStream());

		if (!addressesList.contains(destination)) {
			output.writeByte(BOOTSTRAP_RESPONSE_ERROR_CODE);
			return;
		}
		output.writeByte(BOOTSTRAP_RESPONSE_SUCCESS_CODE);
		output.writeByte(addressesList.size() - 1);
		// mette nella lista tutti gli indirizzi tranne quello della sorgente e quello della destinazione
		for (InetAddress address : addressesList) {
			if (!address.equals(source) && !address.equals(destination))
				output.write(address.getAddress(), 0, 4);
		}
		// mette in fondo alla lista l'indirizzo della destinazione
		output.write(destination.getAddress(), 0, 4);
	}

}
