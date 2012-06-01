import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.util.ArrayList;

/**
 * Connection for a client to the server
 * @author 52694
 *
 */
public class Connection implements Runnable
{
	Socket clientSocket;
	
	static final String THE_PASSWORD ="cheese";//the password
	boolean authorised = false;
	
	BufferedReader inFromClient;//buffered input from client
	DataOutputStream outToClient;//data output to client
	
	ServerState state;//the state that the server is in.
		
	public Connection(Socket clientSocket)
	{
		this.clientSocket = clientSocket;//client socket that the client has connceted through
		state = new UnAuthorised();
		try{
			inFromClient =new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
			outToClient =new DataOutputStream(clientSocket.getOutputStream());
		}catch(Exception e){}
	}
	
	public void run()
	{
		System.out.println("Client connected @ " +clientSocket.getInetAddress());
		while(!clientSocket.isClosed()){

			try{

			    state.update();
		    }catch(Exception e){}
		}
		
	}
	
	//interface defines a server state
	private interface ServerState
	{
		public abstract void update();
	}
	
	//when the client has yet ot enter a valid password
	private class UnAuthorised implements ServerState
	{
		public void update() {
			// read a sentence from the client
		    try {
		    	
		    	String clientIp = clientSocket.getInetAddress().getHostAddress();
		    	if(!inBlockList(clientIp)){//if the client's ip is not blocked (in forbidden.txt)
		    		outToClient.flush();
					String password = inFromClient.readLine();//wait for the password from the client
					if(password.equals(THE_PASSWORD)){//if password is correct
						outToClient.writeBytes("001 PASSWD OK\n");
						state = new Authorised();//move to the authorised state
					}else{
						outToClient.writeBytes("002 PASSWD WRONG\n");//otherwise tell the client and stay in this state
					}
		    	}else{
		    		outToClient.writeBytes("003 REFUSED\n");//if blocked, tell the client
		    		state = new Disconnect();//disconnect the client
		    	}
			} catch (IOException e) {
				e.printStackTrace();
			}
			
		}
		
		private ArrayList<String> getBlockList()
		{
			FileInputStream fStream;
			ArrayList<String> blocklist = new ArrayList<String>();
			try {
				fStream = new FileInputStream("forbidden.txt");
				BufferedReader in = new BufferedReader(new InputStreamReader(fStream));
	            while (in.ready()) {
	                blocklist.add(in.readLine());//adds each line in the forbidden file to the blocked array list.
	            }
	            in.close();//close the forbidden.txt's reader
			} catch (FileNotFoundException e) {
				return new ArrayList<String>(); //if no block list exists, return a blank list
			} catch (IOException e) {
				e.printStackTrace();
			}
			return blocklist;// return the blocklist
		}
		
		private boolean inBlockList(String clientIp)
		{
			ArrayList<String> blocklist = getBlockList();
			String clientDomain = clientSocket.getInetAddress().getCanonicalHostName();//attempts to resolve the hostname of the ip address
			if(blocklist.contains(clientIp) || blocklist.contains(clientDomain)){
				return true; //if the ip/cleint domain is in the list, return true
			}else{
				return false;//otherwise, return false
			}
		}
		
	}
	
	private class Authorised implements ServerState
	{
		public void update() {
			
		    try {
				String command = inFromClient.readLine();// read a command from the client
				//if it is a valid command word
				if(command.equals("exit")){
					state = new Disconnect(); //change state to disconnect
				}else if(command.equals("rls")){
					state = new ListRemoteFiles(); //move to lit remote files state
				}else if(command.startsWith("get ")){
					state = new GetFile(command.substring(4).trim()); //gets the required filename and passes this to the state that will return the file.
				}else if(command.startsWith("put ")){
					state = new PutFile(command.substring(4).trim());
				}else if(!command.equals("")){//if its not a command word but not blank
					outToClient.writeBytes("Command "+command+" not recognised. Type help for a list of valid server commands\n"); //tell the client!
				}else if(command.equals("")){
				}
			} catch (IOException e) {
				e.printStackTrace();
			}	
		}
	}
	
	private class Disconnect implements ServerState
	{
		public void update() {
		    try {
		    	Thread.sleep(4000);//wait 4 seconds
				clientSocket.close(); //close the connection
			} catch (IOException e) {
				e.printStackTrace();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
		}
		
	}
	
	private class ListRemoteFiles implements ServerState
	{
		public void update() {
		    String path = ".";
			File dir = new File(path);
			String[] list = dir.list();
			String output = "";//sets up the output string

			for (int fileNo = 0; fileNo < list.length; fileNo++)  {
				output += list[fileNo]+"\n";
			}

			output += "\n";//for neatness
				
			try {
				outToClient.writeBytes("021 REMOTEFILELIST\n");//tells the client that it will receive a remote file list
				outToClient.writeBytes(output);
				outToClient.writeBytes("022 ENDOFFILELIST\n");//end of remote fiel list.
			} catch (IOException e) {
				e.printStackTrace();
			}
			state = new Authorised();
			
		}
		
	}
	
	private class PutFile implements ServerState
	{
		String fileName;
		public PutFile(String fileName)
		{
			this.fileName = fileName;
		}
		
		public void update() {
			try{
				outToClient.writeBytes("012 SENDFILE "+fileName+"\n");//tells the client to send the file
				String fileExists = inFromClient.readLine();//waits for the file size or file not found response
				if(!fileExists.equals("011 FILE NOT FOUND")){//if file is found
					int fileSize = Integer.parseInt(fileExists);
					byte[] mybytearray = new byte[fileSize];//make new byte array
				    DataInputStream is = new DataInputStream(clientSocket.getInputStream());
				    FileOutputStream fos = new FileOutputStream(fileName);
				    BufferedOutputStream bos = new BufferedOutputStream(fos);
				    outToClient.writeBytes("111 FILESIZEOK\n");//tell the client that it has received the file size and that the client can now transmit the file
				    int bytesRead = 0;
				    while(bytesRead < fileSize-1){
				    	bytesRead+=is.read(mybytearray, bytesRead, mybytearray.length - bytesRead);//reads as much fo the file as it can untill it has read the whole thing
				    }
				    bos.write(mybytearray, 0, bytesRead);//write that out the the hard disk
				    bos.close();//close file
				    System.out.println("File recieved from client.");//the server has recieved a file
				}else{
					throw new FileNotFoundException();//if file doesnt exist
				}
			}catch(FileNotFoundException e){
					System.out.println("File not found on client.");//file not found
			}catch(Exception e){
					e.printStackTrace();
			}
			state = new Authorised();//accept next command
		}
		
	}
	
	private class GetFile implements ServerState
	{
		String fileName;
		public GetFile(String fileName)
		{
			this.fileName = fileName;
		}
		
		public void update() {

			try{
				outToClient.writeBytes("013 RECIEVEFILE "+fileName+"\n");//tell the lient it should prepare to recieve a file
				File myFile = new File (fileName);
				if(!myFile.exists()){//if the file doesnt exist on the server
					throw new FileNotFoundException();//throw a FNF exception
				}
				
				outToClient.writeBytes(myFile.length() + "\n");//sends the file length to the client
				byte[] fileBytes = new byte[(int) myFile.length()];//make new byte array
			      BufferedInputStream bufferedInFromFile = new BufferedInputStream(new FileInputStream(myFile));//setup file reader objects
			      bufferedInFromFile.read(fileBytes, 0, fileBytes.length);//read the file into the byte array
			      bufferedInFromFile.close();//close the buffered reader stream
			      
			      inFromClient.readLine();//wait for a response from the client to say it has received the file size
			      Thread.sleep(100);
			      outToClient.write(fileBytes, 0, fileBytes.length);//write the file to the output stream
			      outToClient.flush();
			    System.out.println("File sent to client");
			}catch(FileNotFoundException e){
				try {
					outToClient.writeBytes("011 FILE NOT FOUND\n");//if the file is not found, tell the client.
				} catch (IOException e1) {
				}
			}catch(IOException e){} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			state = new Authorised();
			
		}
		
	}
	
}
