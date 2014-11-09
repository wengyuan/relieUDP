import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.zip.CRC32;

public class Receiver {
	static int pkt_size = 1000;
	static int last_receive = 0;
	static int time_out = 2000;
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
					int contentLengthByteLength = 0;
					boolean corrupt = false;
						try {
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
						contentLengthByteLength = in_data[in_data[0]+4];
						byte[] contentLengthByte = new byte[contentLengthByteLength];
						System.arraycopy(in_data, in_data[0]+5, contentLengthByte, 0, contentLengthByteLength);
						
						int contentLength = Integer.parseInt(new String(contentLengthByte));
						
						byte[] checkSumcontent = new byte[contentLength+contentLengthByteLength+4];
						byte[] content = new byte[contentLength];

						System.arraycopy(in_data, in_data[0]+1, checkSumcontent, 0, contentLength+contentLengthByteLength+4);
						System.arraycopy(in_data, in_data[0]+contentLengthByteLength+5, content, 0, contentLength);
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
							continue;
						}
						
						if(!corrupt && (last_receive+1)%128 == sequence) {
							last_receive = (last_receive+1)%128;
							if(tag == 0) {
								fileName = new String(content);
								outputFile = new FileOutputStream(directory + fileName);
							}
							if(tag == 1) {
								outputFile.write(content, 0, contentLength);
							}
							
							sendAck(sk3_dst_port, sk3, in_data,
									dst_addr, sequence, acks);
						} else {
							sendCorruptAck(sk3_dst_port, sk3, in_data,
									dst_addr, sequence, acks);
						}
						} catch(Exception e) {
							sendCorruptAck(sk3_dst_port, sk3, in_data,
									dst_addr, sequence, acks);
						}
				
				}
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
		Ack[0] = sequence;
		Ack[1] = acks;

		crc.update(Ack, 0, 2);
		long check_sum = crc.getValue();
		
		byte[] checkSumByte = Long.toString(check_sum).getBytes();
		out_data[0] = (byte) checkSumByte.length;
		System.arraycopy(checkSumByte, 0, out_data, 1, checkSumByte.length);

		DatagramPacket out_pkt = new DatagramPacket(out_data,
				in_data.length, dst_addr, sk3_dst_port);
		last_packet = out_pkt;
		sk3.send(out_pkt);
		
	}
 
	private void sendCorruptAck(int sk3_dst_port, DatagramSocket sk3,
			byte[] in_data, InetAddress dst_addr, byte sequence, byte acks)
			throws IOException {
		// send corrupted ack
		crc.reset();
		byte[] out_data = new byte[pkt_size];
		byte[] Ack = new byte[2];
		Ack[0] = -1;
		Ack[1] = -1;

		crc.update(Ack, 0, 2);
		long check_sum = crc.getValue();
		byte[] checkSumByte = Long.toString(check_sum).getBytes();
		out_data[0] = (byte) checkSumByte.length;
		System.arraycopy(checkSumByte, 0, out_data, 1, checkSumByte.length);

		DatagramPacket out_pkt = new DatagramPacket(out_data,
				in_data.length, dst_addr, sk3_dst_port);
		sk3.send(out_pkt);
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
