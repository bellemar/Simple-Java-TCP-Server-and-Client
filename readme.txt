Author- 52694
Password = cheese

The server is contained within the fss.java file. 
Upon compiling this it will crete several inner classes that relate to the different states of the machine.

The client is contained within the fsc.java file. 
Upon compiling this it will crete several inner classes that relate to the different states of the machine.

Upon conncetion, the user is propted for a password and once this has been entered the client will then accept the following commands:
	lls - 		a purely local command that is not sent to the server at all. it prints a list of files that exist in the 			clients local directory
	rls -		returns a list of files that are in the servers directory, i.e. the same folder as the fss file being run
	get filename -	transfers a file form the server to the client's local directory
	put filename - 	transfers a file from the client to the server's local directory
	exit - 		closes the connection between cleint and server.

The source code includes comprehensive (almost line by line) comments to help understand what each program does.

In general, the server (fss) when run will wait for connections from clients (fsc).  The server will then make a new Conncetion object and this will wait for the password from the client.

The client then sends the password (once entered by the user) and waits for response from the server. If the password is correct the client then moves to a state in which it can accept commands (listed above) and send these to the server.

The server also moves to a state in which it can recieve commands and respond appropriately to these.