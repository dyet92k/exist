/*
 * eXist Request Replayer
 *
 * Release under the BSD License
 *
 * Copyright (c) 2006, Adam retter <adam.retter@devon.gov.uk>
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
 * 		Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 *  	Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.
 *  	Neither the name of the Devon Portal Project nor the names of its contributors may be used to endorse or promote products derived from this software without specific prior written permission.
 *  
 *  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 *  IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 *  IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 *  PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 *  CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 *  SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 *  PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
 *  OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 *  LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
 *  OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 *  ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * $Id$
 */

package org.exist.requestlog;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.Socket;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.border.TitledBorder;


/** Webapplication Descriptor
 * 
 * Request Replayer Simple Swing GUI Application
 * Opens request replay log's as generated by eXist's web-application Descriptor when enabled
 * and can send the request's back to eXist.
 * Useful for load testing and checking memory leaks.
 * 
 * @author Adam Retter <adam.retter@devon.gov.uk>
 * @serial 2006-02-28
 * @version 1.6
 */
public class RequestReplayer extends JFrame {
	private static final long serialVersionUID = 1L;

	/** Handle to the Request Log File */
	private File requestLogFile = null;
	
	//Dialog Controls
	private JTextField txtReplayFilename = null;
	private JLabel lblRequestCount = null;
	private JTextField txtIterations = null;
	private JTextField txtAlternateHost = null;
	private JButton btnStart = null;
	private JTextArea txtStatus = null;
	
	
	/**
	 * Entry point of the program
	 * 
	 * @param args		array of parameters passed in from where the program is executed
	*/
	public static void main(String[] args)
	{
		String fileName = null;
		if ( args.length > 0 )
			fileName = args[0];
		//Instantiate oursel
		// RequestReplayer rr = 
		new RequestReplayer(fileName);
	}
	
	/**
	 * Default Constructor
	 * @param fileName 
	 */
	public RequestReplayer(String fileName) {
		if( fileName != null )
			requestLogFile = new File(fileName);
		initialize();
	}
	
	/**
	 * JDialog Window Event Handler
	 * 
	 * @param e		The event
	 */
	protected void processWindowEvent(WindowEvent e)
	{
		//Close Window Event
	    if(e.getID() == WindowEvent.WINDOW_CLOSING)
	    {
	      this.setVisible(false);
	      this.dispose();
	      System.exit(0);	//why do we need this? shouldnt the above line do the job?
	    }
	}
	
	/**
	 * Initalise Dialog
	 */
	private void initialize()
	{		
		setupGUI();
		
		this.setSize(600, 400);
		this.setVisible(true);
	}
	
	/**
	 *Setup the Dialog's GUI 
	 */
	private void setupGUI()
	{
		this.setTitle("eXist Request Replayer");
		
		//Dialog Content Panel
		JPanel cnt = new JPanel();
		GridBagLayout grid = new GridBagLayout();
		cnt.setLayout(grid);
		this.setContentPane(cnt);
		
		//Constraints for layout
		GridBagConstraints c = new GridBagConstraints();
		c.insets = new Insets(2, 2, 2, 2);
		
		//Panel to hold controls relating to the request log file
		JPanel panelFile = new JPanel();
		panelFile.setBorder(new TitledBorder("Request Log"));
		GridBagLayout panelFileGrid = new GridBagLayout();
		panelFile.setLayout(panelFileGrid);
		
		//filename label
		JLabel lblLogFile = new JLabel("Filename:");
		c.gridx = 0;
		c.gridy = 0;
		c.gridwidth = 1;
		c.anchor = GridBagConstraints.WEST;
		c.fill = GridBagConstraints.NONE;
		panelFileGrid.setConstraints(lblLogFile, c);
		panelFile.add(lblLogFile);
		
		// filename field
		String fileNameInfield = "/usr/local/eXist/request-replay-log.txt";
		if( requestLogFile != null )
			fileNameInfield = requestLogFile.getAbsolutePath();
		txtReplayFilename = new JTextField(fileNameInfield, 24);
		txtReplayFilename.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if(!txtReplayFilename.getText().equals(requestLogFile.getPath())) {
                	//Show a dialog to choose the log file
                	chooseFile();
                }
            }
        });
		c.gridx = 1;
		c.gridy = 0;
		c.anchor = GridBagConstraints.WEST;
		c.fill = GridBagConstraints.NONE;
		panelFileGrid.setConstraints(txtReplayFilename, c);
		panelFile.add(txtReplayFilename);
		
		//filename choose button
		JButton btnChooseFile = new JButton("Choose...");
		btnChooseFile.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                chooseFile();
            }
        });
		c.gridx = 2;
		c.gridy = 0;
		c.anchor = GridBagConstraints.WEST;
		c.fill = GridBagConstraints.NONE;
		panelFileGrid.setConstraints(btnChooseFile, c);
		panelFile.add(btnChooseFile);
		
		//Records count labels
		JLabel lblRequestCountText = new JLabel("Request Count: ");
		c.gridx = 0;
		c.gridy = 2;
		c.anchor = GridBagConstraints.WEST;
		c.fill = GridBagConstraints.NONE;
		panelFileGrid.setConstraints(lblRequestCountText, c);
		panelFile.add(lblRequestCountText);		
		lblRequestCount = new JLabel(new Integer(countRequestRecords()).toString());
		c.gridx = 1;
		c.gridy = 2;
		c.anchor = GridBagConstraints.WEST;
		c.fill = GridBagConstraints.NONE;
		panelFileGrid.setConstraints(lblRequestCount, c);
		panelFile.add(lblRequestCount);
		
		
		//Add the Request file panel to the main content
		c.gridx = 0;
		c.gridy = 0;
		c.anchor = GridBagConstraints.WEST;
		c.fill = GridBagConstraints.NONE;
		grid.setConstraints(panelFile, c);
		cnt.add(panelFile);
		
		//Panel to hold controls relating to the replaying of the requests
		JPanel panelReplay = new JPanel();
		panelReplay.setBorder(new TitledBorder("Replay"));
		GridBagLayout panelReplayGrid = new GridBagLayout();
		panelReplay.setLayout(panelReplayGrid);
		
		//Iterations Label
		JLabel lblIterations = new JLabel("Iterations:");
		c.gridx = 0;
		c.gridy = 0;
		c.anchor = GridBagConstraints.WEST;
		c.fill = GridBagConstraints.NONE;
		panelReplayGrid.setConstraints(lblIterations, c);
		panelReplay.add(lblIterations);
		
		//Iterations field
		txtIterations = new JTextField("1", 5);
		c.gridx = 1;
		c.gridy = 0;		
		c.anchor = GridBagConstraints.WEST;
		c.fill = GridBagConstraints.NONE;
		panelReplayGrid.setConstraints(txtIterations, c);
		panelReplay.add(txtIterations);
		
		//Alternate Host Label
		JLabel lblAlternateHost = new JLabel("Alternate Host:");
		c.gridx = 0;
		c.gridy = 1;
		c.anchor = GridBagConstraints.WEST;
		c.fill = GridBagConstraints.NONE;
		panelReplayGrid.setConstraints(lblAlternateHost, c);
		panelReplay.add(lblAlternateHost);
		
		//Alternate Host Field
		txtAlternateHost = new JTextField(24);
		c.gridx = 1;
		c.gridy = 1;
		c.anchor = GridBagConstraints.WEST;
		c.fill = GridBagConstraints.NONE;
		panelReplayGrid.setConstraints(txtAlternateHost, c);
		panelReplay.add(txtAlternateHost);
		
		//Start Button
		btnStart = new JButton("Start");
		btnStart.setMnemonic('S');
		btnStart.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
            
            	new Thread()
				{
            		public void run()
            		{
            			doReplay();
            		}
            	}.start();
            	
            	//TODO: Could change the above method so iterations happen simultaneously in seperate threads!
            	//Disable the Start Button (Only one Thread running at a time!)
            	btnStart.setEnabled(false);
            }
        });
		c.gridx = GridBagConstraints.CENTER;
		c.gridy = 2;
		c.anchor = GridBagConstraints.CENTER;
		c.fill = GridBagConstraints.HORIZONTAL;
		panelReplayGrid.setConstraints(btnStart, c);
		panelReplay.add(btnStart);
		
		//Add the Replay panel to the main content
		c.gridx = 0;
		c.gridy = 1;
		c.anchor = GridBagConstraints.WEST;
		c.fill = GridBagConstraints.NONE;
		grid.setConstraints(panelReplay, c);
		cnt.add(panelReplay);
		
		//Panel to hold controls for status of replaying of the requests
		JPanel panelStatus = new JPanel();
		panelStatus.setBorder(new TitledBorder("Status"));
		GridBagLayout panelStatusGrid = new GridBagLayout();
		panelStatus.setLayout(panelStatusGrid);
		
		//Status text area with vertical scroll
		txtStatus = new JTextArea(10, 40);
		txtStatus.setEditable(false);
		JScrollPane scrollStatus = new JScrollPane(txtStatus, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		c.gridx = 0;
		c.gridy = 0;
		c.anchor = GridBagConstraints.CENTER;
		c.fill = GridBagConstraints.NONE;
		panelStatusGrid.setConstraints(scrollStatus, c);
		panelStatus.add(scrollStatus);
		
		//Add the Status panel to the main content
		c.gridx = 0;
		c.gridy = 2;
		c.anchor = GridBagConstraints.WEST;
		c.fill = GridBagConstraints.NONE;
		grid.setConstraints(panelStatus, c);
		cnt.add(panelStatus);
		
	}
	
	/**
	 * Counts the number of request records in the request replay log file
	 * 
	 *  @return		The number of request records in the request replay log file
	 */
	private int countRequestRecords()
	{
		//Count of records
		int count = 0;
		
		//if there is no file handle try and setup a handle for the user specified file
		if(requestLogFile == null)
		{
			try
			{
				requestLogFile = new File(txtReplayFilename.getText());
			}
			catch(NullPointerException npe)
			{
				System.err.println("Invalid path for Request file");
				return(0);
			}
		}
		
		//Iterate through the file, incrementing the count for each record found
		//records start with a line starting with "Date: "
		try
		{
			BufferedReader bufRead = new BufferedReader(new FileReader(requestLogFile));
			String line = bufRead.readLine();
			while(line != null)
			{	
				if(line.indexOf("Date:") > -1)
				{
					count++;
				}
				line = bufRead.readLine();
			}
			
			bufRead.close();
		}
		catch(FileNotFoundException fnfe)
		{
			System.err.println("Request file not found");
			return(0);
		}
		catch(IOException ioe)
		{
			System.err.println("An I/O Exception occured whilst reading the Request file");			
			return(count);
		}
		
		return(count);
	}
	
	/**
	 * Event for when the "Choose..." button is clicked, displays a simple
	 * file chooser dialog 
	 * @param  
	 */
	private void chooseFile() {
		JFileChooser fileChooser = new JFileChooser();
		fileChooser.setApproveButtonText("Open");
		fileChooser.setApproveButtonMnemonic('O');
		if( requestLogFile != null ) {
			fileChooser.setCurrentDirectory( requestLogFile.getParentFile() );
			fileChooser.ensureFileIsVisible(requestLogFile);
		}
		int retval = fileChooser.showDialog(this, null);
		if (retval == JFileChooser.APPROVE_OPTION)
		{
			requestLogFile = fileChooser.getSelectedFile();
			txtReplayFilename.setText(requestLogFile.getPath());
			lblRequestCount.setText(new Integer(countRequestRecords()).toString());
		}
	}
	
	/**
	 * Function that takes each request from the log file
	 * and sends it back to the server 
	 */
	private void doReplay()
	{
		RandomAccessFile raFile = null;		//Random Access to Log File
		String line = null;					//Holds a single line from the File
		long offset = 0;					//Offset used for moving back up the file (for preserving carriage returns in a POST request)
		
		//clear the status
		txtStatus.setText("");
		txtStatus.setCaretPosition(txtStatus.getDocument().getLength());
		
		//repeat as specified by the user in the iteration text field	
    	int iterations = new Integer(txtIterations.getText()).intValue();
    	for(int i = 0; i < iterations; i++)
    	{	
    		try
			{
    			//try and open the file
    			raFile = new RandomAccessFile(requestLogFile, "r");
    			
				//loop through the file line by line
				while((line = raFile.readLine()) != null)
				{	
					/*
					 * Each Request record start's with a line in the file starting "Date: "
					 * and each record end's with two empty lines in the file (two carriage returns).
					 * */
					
					//Is this the start of a record
					if(line.indexOf("Date:") > -1)
					{				
						//Yes, process the Record

						StringBuffer bufRequest = new StringBuffer();	//buffer to hold each request record from the file
						String server = null;	//server to send the request to
						int port = 80;			//server port to send the request to
						
						//Update Status for user
						txtStatus.append("Iteration: " + (i+1) + ", Sending Request from " + line + System.getProperty("line.separator"));
						txtStatus.setCaretPosition(txtStatus.getDocument().getLength());
						
						//Loop through each line of the file that is part of this record
						while((line = raFile.readLine()) != null)
						{
							//is this an empty line (i.e. carriage return)
							if(line.length() != 0)
							{
								//NO, not an empty line (i.e. carriage return)
								
								//Store the line in the buffer
								bufRequest.append(line + System.getProperty("line.separator"));
								
								//host for request
								if(line.indexOf("Host:") > -1)
								{
									//has the user specified an alternate host?
									if(txtAlternateHost.getText().length() == 0)
									{
										//get host from request
										String host = line.substring(new String("Host: ").length());
										server = host.substring(0, host.indexOf(":"));
										port = new Integer(host.substring(host.indexOf(":") + 1)).intValue();
									}
									else
									{
										//get user specified alternate host
										server = txtAlternateHost.getText().substring(0, txtAlternateHost.getText().indexOf(":"));
										port = new Integer(txtAlternateHost.getText().substring(txtAlternateHost.getText().indexOf(":") + 1)).intValue();
									}
								}
							}
							else
							{
								//YES, an empty line (i.e. carriage return)
								
								offset = raFile.getFilePointer(); //get the position in case this isnt the end of record indicator, then we can roll back
							
								//we have had an empty line, is it followed by another empty line? 
								//if so this indicates the end of this record, so break out of the inner loop and read the next record
								if(raFile.readLine().length() == 0 || raFile.length() == offset)
								{
									//do request
									try
									{
										//Connect a socket to the server
										Socket socReq = new Socket(server, port);
										
										OutputStream socReqOut = socReq.getOutputStream();
										DataOutputStream os = new DataOutputStream(socReqOut);
										
										//Write Request to the socket
										os.writeBytes(bufRequest.toString());
										os.flush();
										os.close();
										socReqOut.close();
										
										//Close the socket
										socReq.close();
									}
									catch(IOException ioe)
									{
										System.err.println("An I/O Exception occured whilst writting a Request to the server");
										System.err.println(ioe.getMessage());
									}
									
									synchronized (this) {
										// wait 200 milliseconds before sending next request
										try {
											wait(200);
										} catch (InterruptedException e) {
										}
									}
									//break out of this inner while loop, i.e. next record
									break;
								}
								else
								{
									//wasnt the end of record marker so reset file position
									raFile.seek(offset);
									
									//Also Store the carriage return that we checked for in the buffer,
									//as this is part of the Request data not the end of file marker
									bufRequest.append(System.getProperty("line.separator"));
								}
							}
						}
					}
					
				}
				//close the file
		    	raFile.close();
			}
			catch(IOException ioe)
			{
				System.err.println("An I/O Exception occured whilst reading the Request file");
				//We have errored so enable the start button so the user can do it again if they want to!
		    	btnStart.setEnabled(true);
				return;
			}
    	}
    	//We have finished so enable the start button so the user can do it again if they want to!
    	btnStart.setEnabled(true);
    }
	
}
