import java.io.*;
import java.net.*;

/**
 * Class for building multiple threaded conncetions to allow multiple clinets to conncet at once
 * @author 52694
 */
class fss {
	
	public fss(int port){
		try{
			ServerSocket connectionSocket = new ServerSocket(port);//make a new socket to accept connections on
			System.out.println("Server Running");
			while (true) {//forever!
				Socket clientSocket = connectionSocket.accept();//wait for a conncetion from a client
				Connection con = new Connection(clientSocket);//make a new connection object
				new Thread(con).start(); //start that threaded object.
			}
		}catch(SocketException e){
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
    public static void main (String args[]) throws Exception
    {
    	try{
			int port = Integer.parseInt(args[0]);
			new fss(port);
		}catch(NumberFormatException e){
			System.out.println("Invalid port number. Please enter an integer.");
		}catch(ArrayIndexOutOfBoundsException ae){
			System.out.println("No port number entered. Please enter a port number");
		}
    }
    
	
}

