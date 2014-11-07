import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.zip.CRC32;

public class Receiver {
	static int pkt_size = 512;
	static int last_receive = 0;
	static int time_out = 10000;
	DatagramPacket last_packet = null;
	CRC32 crc = new CRC32();

	public Receiver(int sk2_dst_port, int sk3_dst_port, String directory) {
		DatagramSocket sk2, sk3;
		System.out.println("sk2_dst_port=" + sk2_dst_port + ", "
				+ "sk3_dst_port=" + sk3_dst_port + ".");
		
		//create the directory if it is not existed
		File file = new File(directory);
		if (!file.exists()) {
			file.mkdir();
		}

		//create file output stream
		FileOutputStream outputFile = null;
		
		// create sockets
		try {
			sk2 = new DatagramSocket(sk2_dst_port);
			sk3 = new DatagramSocket();
			try {
				byte[] in_data = new byte[pkt_size];
				
				DatagramPacket in_pkt = new DatagramPacket(in_data,
						in_data.length);
				InetAddress dst_addr = InetAddress.getByName("127.0.0.1");
				
				String fileName = null;

				while(true) {
					byte sequence = 0;
					byte acks = 0;
					byte tag = 0;
					boolean corrupt = false;
					
						try {
							sk2.setSoTimeout(time_out);
							sk2.receive(in_pkt);
						} catch(SocketTimeoutException e) {
							if(fileName == null) {
								continue;
							} else {
								break;
							}
						}
						crc.reset();
						sequence = in_data[in_data[0]+1];
						acks = in_data[in_data[0]+2];
						tag = in_data[in_data[0]+3];

						System.out.println(sequence + " " + acks + " " + in_data[in_data[0]+3]);
						
						int fileLength = in_data[in_data[0]+4];
						byte[] checkSumcontent = new byte[fileLength+4];
						byte[] content = new byte[fileLength];
						for(int i = in_data[0]+1; i< in_data[0]+ 5 + fileLength; i++) {
							checkSumcontent[i - in_data[0]- 1] = in_data[i];
							if(i >= in_data[0] + 5 && i < in_data[0]+ 5 + fileLength) {
								content[i - in_data[0]- 5] = in_data[i];
							}
						}
						crc.update(checkSumcontent);
						

						long check_sum = crc.getValue();
						byte[] checkSum = Long.toString(check_sum).getBytes();
						
						int checkSumLength = in_data[0];
						if(checkSumLength != checkSum.length) {
							corrupt = true;
						} else {
							for (int i = 1; i <= checkSumLength; i++) {
								if (checkSum[i - 1] != in_data[i]) {
									corrupt = true;
								}
							}
						}
						
						if(!corrupt && last_receive == sequence) {
							sk3.send(last_packet);
							System.out.println("resend");
							continue;
						}
						
						if(!corrupt) {
							last_receive = sequence;
							if(tag == 0) {
								System.out.println("name");
								fileName = new String(content);
								outputFile = new FileOutputStream(directory + "/" + fileName);
							}
							if(tag == 1) {

								System.out.println("content");
								outputFile.write(content);
							}
							
							sendAck(sk3_dst_port, sk3, in_data,
									dst_addr, sequence, acks);
						} else {
							sendCorruptAck(sk3_dst_port, sk3, in_data,
									dst_addr, sequence, acks);
						}
				
				}
							
				outputFile.write(in_data);
				
				outputFile.close();
				
				
				/*
				while (true) {
					// receive packet
					
					sk2.receive(in_pkt);
					crc.reset();
					byte[] checkSum = new byte[in_data[0]];
					for(int i = 1; i <= in_data[0]; i++) {
						checkSum[i-1] = in_data[i];
					}
					
					byte sequence = in_data[in_data[0]+1];
					byte acks = in_data[in_data[0]+2];
					byte fileLength = in_data[in_data[0]+3];
					byte[] content = new byte[fileLength];
					for(int i = in_data[0]+4; i< in_data[0]+4 + fileLength; i++) {
						content[i - in_data[0]- 4] = in_data[i];
					}
					crc.update(content);

					if(crc.getValue() == Long.parseLong(new String(checkSum))) {
						output.write(content, 0, fileLength);
					} else {
						System.out.println("cor");
					}
					
					// send received packet
					crc.reset();
					byte[] out_data = new byte[pkt_size];
					byte[] Ack = new byte[2];
					Ack[0] = (byte) (sequence%128);
					Ack[1] = (byte) (acks%128);

					System.out.println("sequence: " + Ack[0]);
					System.out.println("Ack: " + Ack[1]);
					crc.update(Ack, 0, 2);
					long check_sum = crc.getValue();
					System.out.println(check_sum);
					byte[] checkSumByte = Long.toString(check_sum).getBytes();
					out_data[0] = (byte) checkSumByte.length;
					for(int i = 1; i <= checkSumByte.length; i++) {
						out_data[i] = checkSumByte[i-1];
					}
					out_data[checkSumByte.length+1] = sequence;
					out_data[checkSumByte.length+2] = acks;
					
					DatagramPacket out_pkt = new DatagramPacket(out_data,
							in_data.length, dst_addr, sk3_dst_port);
					sk3.send(out_pkt);

				}
				*/
			} catch (Exception e) {
				e.printStackTrace();
				System.exit(-1);
			} finally {
				sk2.close();
				sk3.close();
			}
		} catch (SocketException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
	}

	private void sendAck(int sk3_dst_port, DatagramSocket sk3, byte[] in_data,
			InetAddress dst_addr, byte sequence, byte acks) throws IOException {
		// send received Ack
		crc.reset();
		byte[] out_data = new byte[pkt_size];
		byte[] Ack = new byte[2];
		Ack[0] = (byte) (sequence % 128);
		Ack[1] = (byte) (acks % 128);
		System.out.println(Ack[0]);
		System.out.println(Ack[1]);

		crc.update(Ack, 0, 2);
		long check_sum = crc.getValue();
		byte[] checkSumByte = Long.toString(check_sum).getBytes();
		out_data[0] = (byte) checkSumByte.length;
		for (int i = 1; i <= checkSumByte.length; i++) {
			out_data[i] = checkSumByte[i - 1];
		}
		out_data[checkSumByte.length + 1] = sequence;
		out_data[checkSumByte.length + 2] = acks;

		DatagramPacket out_pkt = new DatagramPacket(out_data,
				in_data.length, dst_addr, sk3_dst_port);
		last_packet = out_pkt;
		sk3.send(out_pkt);
		
	}

	private void sendCorruptAck(int sk3_dst_port, DatagramSocket sk3,
			byte[] in_data, InetAddress dst_addr, byte sequence, byte acks)
			throws IOException {
		// send corrupted ack
		System.out.println("corrupted");
		crc.reset();
		byte[] out_data = new byte[pkt_size];
		byte[] Ack = new byte[2];
		Ack[0] = -1;
		Ack[1] = -1;

		crc.update(Ack, 0, 2);
		long check_sum = crc.getValue();
		System.out.println(check_sum);
		byte[] checkSumByte = Long.toString(check_sum).getBytes();
		out_data[0] = (byte) checkSumByte.length;
		for (int i = 1; i <= checkSumByte.length; i++) {
			out_data[i] = checkSumByte[i - 1];
		}
		out_data[checkSumByte.length + 1] = sequence;
		out_data[checkSumByte.length + 2] = acks;

		DatagramPacket out_pkt = new DatagramPacket(out_data,
				in_data.length, dst_addr, sk3_dst_port);
		sk3.send(out_pkt);

		System.out.println("sent corrupted data");
	}

	public static void main(String[] args) {
		// parse parameters
		if (args.length != 3) {
			System.out.println(args.length);
			System.err
					.println("Usage: java TestSender sk1_dst_port, sk4_dst_port");
			System.exit(-1);
		} else
			new Receiver(Integer.parseInt(args[0]), Integer.parseInt(args[1]), args[2]);
	}
}