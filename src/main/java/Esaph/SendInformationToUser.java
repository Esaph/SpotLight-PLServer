/*
 *  Copyright (C) Esaph, Julian Auguscik - All Rights Reserved
 *  * Unauthorized copying of this file, via any medium is strictly prohibited
 *  * Proprietary and confidential
 *  * Written by Julian Auguscik <esaph.re@gmail.com>, March  2020
 *
 */

package Esaph;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.security.KeyStore;
import java.sql.SQLException;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import org.json.JSONArray;
import org.json.JSONObject;
import com.mysql.jdbc.Connection;
import com.mysql.jdbc.PreparedStatement;

import PLServerMain.PostLocationServer;

public class SendInformationToUser extends Thread
{
	private static final String KeystoreFilePathClient = "/usr/server/serverMSG.jks";
	private static final String TrustStoreFilePathClient = "/usr/server/clienttruststoreFORSERVER.jks";
	private static final String KeystorePassClient = "50b605f02e";
	private static final String TruststorePasswordClient = "28612@1587";

	private PostLocationServer plServer;
	private LogUtilsEsaph logUtilsRequest;
	private JSONObject message;
	private Connection connection;
	private static final String queryInsertNewMesage = "INSERT INTO Messages (UID_RECEIVER, MESSAGE) values (?, ?)";
	
	public SendInformationToUser(JSONObject message, LogUtilsEsaph logUtilsRequest, PostLocationServer plServer)
	{
		this.plServer = plServer;
		this.logUtilsRequest = logUtilsRequest;
		this.message = message;
	}
	
	private Connection getConnectionToSql() throws InterruptedException, SQLException
	{
		return (Connection) plServer.getPLServerPool().getConnectionFromPool();
	}
	
	@Override
	public void run()
	{ //Hier werden keine überprüfungen benötigt, nur lediglich ob die freunde sind.
		try
		{
			if(!this.message.has("TIME"))
			{
				this.message.put("TIME", System.currentTimeMillis());
			}
			
			this.connection = this.getConnectionToSql();
			SSLContext sslContext;
			KeyStore trustStore = KeyStore.getInstance("JKS");
			trustStore.load(new FileInputStream(SendInformationToUser.TrustStoreFilePathClient), SendInformationToUser.TruststorePasswordClient.toCharArray());
			KeyStore keystore = KeyStore.getInstance("JKS");
			keystore.load(new FileInputStream(SendInformationToUser.KeystoreFilePathClient), SendInformationToUser.KeystorePassClient.toCharArray());
			KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
			kmf.init(keystore, SendInformationToUser.KeystorePassClient.toCharArray());

			TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
			tmf.init(trustStore);

			sslContext = SSLContext.getInstance("TLS"); 
			TrustManager[] trustManagers = tmf.getTrustManagers(); 
			sslContext.init(kmf.getKeyManagers(), trustManagers, null);
			
		    SSLSocketFactory sslClientSocketFactory = sslContext.getSocketFactory();
			
			SSLSocket socket = (SSLSocket) sslClientSocketFactory.createSocket("127.0.0.1", 1030);
			BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()));
            socket.setSoTimeout(10000);
            writer.println(this.message.toString());
            writer.flush();
            socket.close();
            writer.close();
            reader.close();
            this.logUtilsRequest.writeLog("Message sent");
		}
		catch(Exception ec)
		{
			this.logUtilsRequest.writeLog("SendInformationToUser failed: " + ec);

			try
			{
				JSONArray RECEIVERS = this.message.getJSONArray("EMPF");
				this.message.remove("EMPF");

				for(int counter = 0; counter < RECEIVERS.length(); counter++)
				{
					PreparedStatement prStoreMessage = (PreparedStatement) connection.prepareStatement(SendInformationToUser.queryInsertNewMesage);
					prStoreMessage.setLong(1, RECEIVERS.getLong(counter));
					prStoreMessage.setString(2, this.message.toString());
					prStoreMessage.executeUpdate();
					prStoreMessage.close();
				}
			}
			catch(Exception ecFATAL)
			{
				this.logUtilsRequest.writeLog("SendInformationToUser failed to store failed msg to database (shouldSaveIt) was true: " + ecFATAL);
			}
		}
		finally
		{
			this.connection = this.plServer.getPLServerPool().returnConnectionToPool(this.connection);
		}
	}
}




