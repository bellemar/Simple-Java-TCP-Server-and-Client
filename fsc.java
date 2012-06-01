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

/**
 * Class for running the client.
 * @author 52694
 */
public class fsc
{
	BufferedReader inFromServer;
	DataOutputStream outToServer;
	BufferedReader inFromUser;
	
	Socket clientSocket;
	
	ClientState state;
		
	public fsc(String host, int port)
	{
		inFromUser = new BufferedReader(new InputStreamReader(System.in));
		state = new UnAuthorised();
		try{
			this.clientSocket = new Socket(host, port);
			inFromServer =new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
			outToServer =new DataOutputStream(clientSocket.getOutputStream());
		}catch(Exception e){}
		run();
	}
	
	public static void main(String[] args)
	{
		try{
			int port = Integer.parseInt(args[1]);
			new fsc(args[0], port);
		}catch(NumberFormatException e){
			System.out.println("Invalid port number. Please enter an integer.");
		}
	}
	
	private void run(){
		if(clientSocket != null){
			while(!clientSocket.isClosed()){//while the client is connected to a server
	
				try{
				    state.update(); //runs the state's logic
			    }catch(Exception e){}
			}
		}else{
			System.out.println("Could not conncet to server, check host name and port numbr, then try again.");
		}
	}
	
	//interface for a client state
	private interface ClientState
	{
		public abstract void update();
	}
	
	//requests the password from the client and waits for an appropriate response
	private class UnAuthorised implements ClientState
	{
		public void update() {
			// read a sentence from the client
		    try {
		    	System.out.print("Enter Password:");
		    	String input = inFromUser.readLine();//reads the password in from the user
		    	
	    		outToServer.writeBytes(input+"\n");//sends password to the client
				String fromServer =  inFromServer.readLine();//waits for server response
				if(fromServer.equals("001 PASSWD OK")){
					System.out.print("Password Correct.\n");
					state = new Authorised();//if correct, move to the authorised state
				}else if(fromServer.equals("002 PASSWD WRONG")){
					System.out.print("Password Incorrect.\n");//if wrong, simply move stay in this state and continue asking for password
				}else if(fromServer.equals("003 REFUSED")){
					System.out.print("Conncetion Refused.\n");//if the client is in the block list, disconnect it.
					state = new Disconnect();
				}

			} catch (IOException e) {
				e.printStackTrace();
			}
			
		}
	}
	
	//The client will be in this state is the password has been entered correctly. 
	//all other states return to here once they have finished their update method.
	private class Authorised implements ClientState
	{
		public void update() {
			
		    try {
		    	//re initalize in and out streams
		    	inFromServer =new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
				outToServer =new DataOutputStream(clientSocket.getOutputStream());
				
				String command = inFromUser.readLine();// read a command from the user
				//if its a client side command,
				if(command.equals("lls")){ //such as lls
		    		state = new ListLocalFiles(); //move to the list local files state
		    	}else if(command.equals("exit")){ //or exit
		    		state = new Disconnect(); //move to the disconnect state
		    		outToServer.writeBytes(command + "\n");//tells the server to disconnect too.
		    	}else{ //other wise it is a server side command and 
					outToServer.writeBytes(command + "\n"); //must be sent to the server
					String response = inFromServer.readLine(); //wait for servers response to command
					if(response.startsWith("012 SENDFILE ")){ //if the server wants a file sent
						state = new SendFile(response.substring(13)); //move to this state, (pass in the filename)
					}else if(response.startsWith("013 RECIEVEFILE ")){ //if the server wants to send a file to the client
						state = new RecieveFile(response.substring(16)); //move to the receive state (pass in the file name)
					}else if(response.startsWith("011 FILE NOT FOUND")){ //if it's a file not found command (should not be at this point)
						System.out.println("Remote file does not exist.");//tell the user
					}else if(response.equals("021 REMOTEFILELIST")){//if the server wants to send a list of remote files
						state = new ListRemoteFiles();//move to this state
					}
		    	}
			} catch (IOException e) {
				e.printStackTrace();
			}	
		}
	}
	
	private class Disconnect implements ClientState
	{
		public void update() {
		    try {
		    	System.out.println("Closing conncetion");
		    	Thread.sleep(1000);//wait 4 seconds
				clientSocket.close(); //close the connection
			} catch (IOException e) {
				System.out.println("IO Error, please check connection to server");
			} catch (InterruptedException e) {
				//do nothing if the pause is interupted
			}
			
		}
		
	}
	
	private class ListLocalFiles implements ClientState
	{
		public void update() {
		    String localPath = ".";
			File dir = new File(localPath);

			String[] list = dir.list();
			String output = ""; //initialise an output string

			for (int i = 0; i < list.length; i++)  {
				output += list[i]+"\n"; //adds each file name in the array of file name to the output string
			}

			output += "\n";//add a new line at the end for neatness!
				
			System.out.print(output); //write it to the screen
			state = new Authorised(); //move back to the authorised state for next command
			
		}
		
	}
	
	private class ListRemoteFiles implements ClientState
	{
		public void update() {
			try {
				String file = inFromServer.readLine();
			
				while(!file.equals("022 ENDOFFILELIST")){//while the end of the file list from the server has not been reached
					System.out.println(file);//print the file's name
					file = inFromServer.readLine();//get the next one
				}
				System.out.println(); //nw blank line for neatness
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			state = new Authorised();//back to the authorised state for next command
		}
		
	}
	
	private class SendFile implements ClientState
	{
		String fileName;
		public SendFile(String fileName)
		{
			this.fileName = fileName; //filename taken from the original put command
		}
		
		public void update() {
			try{
				File myFile = new File (fileName);
				if(!myFile.exists()){ //if the file doesn't exist
					throw new FileNotFoundException(); //throw a file not found exception (handled later)
				}
				
				outToServer.writeBytes(myFile.length() + "\n");//sends the file length to the server
				byte[] fileBytes = new byte[(int) myFile.length()];//make new byte array
			    BufferedInputStream bufferedInFromFile = new BufferedInputStream(new FileInputStream(myFile));//setup file reader objects
			    bufferedInFromFile.read(fileBytes, 0, fileBytes.length);//read the file into the byte array
			    bufferedInFromFile.close();//close the buffered reader stream
			      
			    inFromServer.readLine();//wait for a response from the server to say it has received the file size
			    Thread.sleep(100);//wait for a short period of time to make extra sure that the server is ready to accept.
			    outToServer.write(fileBytes, 0, fileBytes.length);//write the file to the output stream
			    outToServer.flush();
			    System.out.println("File sent to server");
			}catch(FileNotFoundException e){
				try {
					outToServer.writeBytes("011 FILE NOT FOUND\n");//if the file is not found, tell the client.
					System.out.println("File not found in local folder.");
				} catch (IOException e1) {
				}
			}catch(IOException e){
				
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			state = new Authorised();
		}
	}
	
	private class RecieveFile implements ClientState
	{
		String fileName;
		public RecieveFile(String fileName)
		{
			this.fileName = fileName;
		}
		
		public void update() {
			
			try{
				String fileExists = inFromServer.readLine();//gets a respons from server re whether the file exist or (if it does, it's size)
				if(!fileExists.equals("011 FILE NOT FOUND")){//if file IS found
					int fileSize = Integer.parseInt(fileExists);//make it an int
					byte[] mybytearray = new byte[fileSize];//make a new byte array
				    DataInputStream is = new DataInputStream(clientSocket.getInputStream());//make a data input stream from the socket
				    FileOutputStream fos = new FileOutputStream(fileName);
				    BufferedOutputStream bos = new BufferedOutputStream(fos);//build a Buffered output stream to write the file to the disk
				    outToServer.writeBytes("111 FILESIZEOK\n");//tell the server that the file size has been recieved ok
				    int bytesRead = 0;
				    while(bytesRead < fileSize-1){//read untill the whole file has been got
				    	bytesRead+=is.read(mybytearray, bytesRead, mybytearray.length - bytesRead);//read in as many bytes as possible
				    }
				    bos.write(mybytearray, 0, bytesRead);//write these bytes to the file
				    bos.close();//close the file
				    System.out.println("File recieved from server.");//tell the user of it's success
				}else{
					throw new FileNotFoundException();//if file not found, throw a new FNF exception
				}
			}catch(FileNotFoundException e){
				System.out.println("File not found on remote server.");//tell the user that the files not been found on server
			}catch(Exception e){
				e.printStackTrace();
			}
			state = new Authorised();//go back to the authorised state to wait for the next command
		}
	}
}
