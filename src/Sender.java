import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.zip.CRC32;

public class Sender {
	static int pkt_size = 1000;
	static int time_out = 500;
	
	static byte sequenceNum = 0;
	static byte baseSequence = 0;
	static byte Acks = 64;
	static byte tag_fileName = 0;
	static byte tag_content = 1;
	
	static boolean forward = false;
	static boolean send = true;
	static boolean completed = false;
	
	static int count = 0;
	
	CRC32 crc = new CRC32();

	public class OutThread extends Thread {
		private DatagramSocket sk_out;
		private int dst_port;
		private int recv_port;
		private String directory;
		private String outputFileName;
		
		public OutThread(DatagramSocket sk_out, int dst_port, int recv_port) {
			this.sk_out = sk_out;
			this.dst_port = dst_port;
			this.recv_port = recv_port;
		}
		
		public OutThread(DatagramSocket sk_out, int dst_port, int recv_port, String directory, String outputFileName) {
			this.sk_out = sk_out;
			this.dst_port = dst_port;
			this.recv_port = recv_port;
			this.directory = directory;
			this.outputFileName = outputFileName;
		}

		public void run() {
			try {
				byte[] out_data = new byte[pkt_size];
				InetAddress dst_addr = InetAddress.getByName("127.0.0.1");
				
				byte[] fileName = outputFileName.getBytes();
				
				try {
					//start sending file name over to receiver
					sendFileName(out_data, dst_addr, fileName, fileName.length, tag_fileName);

					FileInputStream inputFile = new FileInputStream(directory);
					byte[] content = new byte[800];
					int len = 0;
					//start sending file content to receiver
					while ((len = inputFile.read(content)) != -1) {
						out_data = new byte[pkt_size];
						forward = false;
						send = true;
						sendFileName(out_data, dst_addr, content, len, tag_content);
					}
					inputFile.close();
					completed = true;
				} catch (Exception e) {
					e.printStackTrace();
				} finally {
					sk_out.close();
				}
				
				
			} catch (Exception e) {
				e.printStackTrace();
				System.exit(-1);
			}
		}
		
		

		public void sendFileName(byte[] out_data, InetAddress dst_addr,
				byte[] content, int length, int tag) throws IOException {
				out_data = packaging(out_data, content, length, tag);
				
					// send the packet
					int count = 0;
					while(!forward) {
						while(!send) {
						}
							send = false;
							DatagramPacket out_pkt = new DatagramPacket(out_data,
									out_data.length, dst_addr, dst_port);
							sk_out.send(out_pkt);
					}
					
					System.out.println("out: " +  "currentCount: " + count);
		}

		private byte[] packaging(byte[] out_data, byte[] content, int contentLength, int tag) {
			sequenceNum = (byte) ((++sequenceNum)%128);
			Acks = (byte) ((++Acks)%128);
			byte[] contentLengthByte = Integer.toString(contentLength).getBytes();
			int contentLengthByteLength = contentLengthByte.length;
			byte[] checkSumContent = new byte[contentLength+4+contentLengthByteLength];
			
			checkSumContent[0] = sequenceNum;
			checkSumContent[1] = Acks;
			checkSumContent[2] = (byte) tag;
			checkSumContent[3] = (byte) contentLengthByteLength;
			for(int i = 4; i < contentLengthByteLength+4; i++) {
				checkSumContent[i] = contentLengthByte[i-4];
			}
 			
			System.out.println(sequenceNum +  " " + Acks + " " + tag + " " + checkSumContent[4] + " " + checkSumContent[5] + " "+ checkSumContent[6]);
			for(int i = contentLengthByteLength+4; i < checkSumContent.length; i++) {
				checkSumContent[i] = content[i-contentLengthByteLength-4];
			}
			
			crc.reset();
			crc.update(checkSumContent, 0, checkSumContent.length);
			long checkSum = crc.getValue();
			byte[] checkSumByte = Long.toString(checkSum).getBytes();
			
			out_data[0] = (byte) checkSumByte.length;
			for(int i = 1; i <= checkSumByte.length; i++) {
				out_data[i] = checkSumByte[i-1];
			}
			
			for(int i = checkSumByte.length+1; i < checkSumByte.length + checkSumContent.length + 1; i++) {
				out_data[i] = checkSumContent[i-checkSumByte.length-1];
			}
			return out_data;
	
		}
	}

	public class InThread extends Thread {
		private DatagramSocket sk_in;

		public InThread(DatagramSocket sk_in) {
			this.sk_in = sk_in;
		}

		public void run() {
			try {
				byte[] in_data = new byte[pkt_size];
				baseSequence++;
				DatagramPacket in_pkt = new DatagramPacket(in_data,
						in_data.length);
				
				try {
					while(!completed) {
						byte[] checkSum = null;
						byte[] Ack = null;
						long check_sum = 0;
						boolean corrupt = false;
						
						sk_in.setSoTimeout(time_out);
						try{
						sk_in.receive(in_pkt);
						} catch (SocketTimeoutException e) {
							send = true;
							System.out.println("time_out");
							continue;
						}
						System.out.println("receive");
						crc.reset();
						Ack = new byte[2];
						Ack[0] = (byte) (baseSequence % 128);
						Ack[1] = (byte) ((baseSequence + 64) % 128);
						System.out.println(Ack[0]);
						System.out.println(Ack[1]);

						crc.update(Ack, 0, 2);

						long check = crc.getValue();
						byte[] checks = Long.toString(check).getBytes();

						int checkSumLength = in_data[0];
						if(checkSumLength != checks.length) {
							corrupt = true;
						} else {
							for (int i = 1; i <= checkSumLength; i++) {
								if (checks[i - 1] != in_data[i]) {
									corrupt = true;
								}
							}
						}
						System.out.println(crc.getValue());
						if (!corrupt) {
							forward = true;
							send = true;
							baseSequence = (byte) ((++baseSequence)%128);
							System.out.println("not cor");
						} else {
							send = true;
							System.out.println("cor");
						}

					}
				} catch (Exception e) {
					e.printStackTrace();
				} finally {
					sk_in.close();
				}
			} catch (Exception e) {
				e.printStackTrace();
				System.exit(-1);
			}
		}
	}

	public Sender(int sk1_dst_port, int sk4_dst_port, String directory, String outputFileName) {
		DatagramSocket sk1, sk4;
		System.out.println("sk1_dst_port=" + sk1_dst_port + ", "
				+ "sk4_dst_port=" + sk4_dst_port + ".");

		try {
			// create sockets
			sk1 = new DatagramSocket();
			sk4 = new DatagramSocket(sk4_dst_port);

			// create threads to process data
			InThread th_in = new InThread(sk4);
			OutThread th_out = new OutThread(sk1, sk1_dst_port, sk4_dst_port, directory, outputFileName);
			th_in.start();
			th_out.start();
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(-1);
		}
	}

	public static void main(String[] args) {
		// parse parameters
		if (args.length != 4) {
			System.out.println(args.length);
			System.err
					.println("Usage: java TestSender sk1_dst_port, sk4_dst_port");
			System.exit(-1);
		} else
			new Sender(Integer.parseInt(args[0]), Integer.parseInt(args[1]), args[2], args[3]);
	}
}
