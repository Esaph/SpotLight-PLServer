/*
 *  Copyright (C) Esaph, Julian Auguscik - All Rights Reserved
 *  * Unauthorized copying of this file, via any medium is strictly prohibited
 *  * Proprietary and confidential
 *  * Written by Julian Auguscik <esaph.re@gmail.com>, March  2020
 *
 */

package PLServerMain;

import java.awt.Dimension;
import java.awt.Image;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

import Commands.*;
import Esaph.*;
import com.mysql.jdbc.Connection;
import jdk.jshell.spi.ExecutionControl;
import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class PostLocationServer extends Thread
{
	private LogUtilsEsaph logUtilsMain;
	private static final String mainServerLogPath = "/usr/server/Log/PLServer/";
	private static final String ServerType = "PostLocationServer";
	private static final String placeholder = "PostLocationServer: ";
	private SSLServerSocket serverSocket;
	private static final int port = 1031;
	private final HashMap<String, Integer> connectionMap = new HashMap<String, Integer>();
	private SQLPool pool;

	//FCM INFORMATION HANDLER;

	private static final ThreadPoolExecutor executorSubThreads = new ThreadPoolExecutor(Runtime.getRuntime().availableProcessors(),
			100,
			15,
			TimeUnit.SECONDS,
			new LinkedBlockingDeque<Runnable>(100),
			new ThreadPoolExecutor.CallerRunsPolicy());

	private static final ThreadPoolExecutor executorMainThread = new ThreadPoolExecutor(Runtime.getRuntime().availableProcessors(),
			100,
			15,
			TimeUnit.SECONDS,
			new LinkedBlockingDeque<Runnable>(100),
			new ThreadPoolExecutor.CallerRunsPolicy());


	public ThreadPoolExecutor getExecutorSubThreads()
	{
		return executorSubThreads;
	}


	public SQLPool getPLServerPool()
	{
		return this.pool;
	}


	public PostLocationServer() throws IOException
	{
		logUtilsMain = new LogUtilsEsaph(new File(PostLocationServer.mainServerLogPath), PostLocationServer.ServerType, "127.0.0.1", -100);
		Timer timer = new Timer();
		timer.schedule(new UnfreezeConnections(), 0, 60000);
		try
		{
			pool = new SQLPool();
			this.logUtilsMain.writeLog(PostLocationServer.placeholder + "Thread pool loaded().");
		}
		catch(Exception ec)
		{
			this.logUtilsMain.writeLog(PostLocationServer.placeholder + "Thread pool failed to load: " + ec);
		}
	}


	public void startServer()
	{
		try
		{
			this.initSSLKey();
			SSLServerSocketFactory sslServerSocketFactory = this.sslContext.getServerSocketFactory();
			this.serverSocket = (SSLServerSocket) sslServerSocketFactory.createServerSocket(PostLocationServer.port);
			this.start();
			this.logUtilsMain.writeLog("server started.");
		}
		catch(Exception io)
		{
			this.logUtilsMain.writeLog("Exception(Starting server): " + io);
			System.exit(0);
		}
	}


	private static final String KeystoreFilePath = "/usr/server/ECCMasterKey.jks";
	private static final String TrustStoreFilePath = "/usr/server/servertruststore.jks";




	private static final String KeystorePass = "8db3626e47";
	private static final String TruststorePassword = "842407c248";
	private SSLContext sslContext;

	private void initSSLKey() throws KeyStoreException, NoSuchAlgorithmException, CertificateException, FileNotFoundException, IOException, UnrecoverableKeyException, KeyManagementException
	{
		this.logUtilsMain.writeLog(PostLocationServer.placeholder + "Setting up SSL-Encryption");
		KeyStore trustStore = KeyStore.getInstance("JKS");
		trustStore.load(new FileInputStream(PostLocationServer.TrustStoreFilePath), PostLocationServer.TruststorePassword.toCharArray());
		this.logUtilsMain.writeLog(PostLocationServer.placeholder + "SSL-Encryption TrustStore VALID.");
		KeyStore keystore = KeyStore.getInstance("JKS");
		keystore.load(new FileInputStream(PostLocationServer.KeystoreFilePath), PostLocationServer.KeystorePass.toCharArray());
		this.logUtilsMain.writeLog(PostLocationServer.placeholder + "SSL-Encryption Keystore VALID.");
		KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
		kmf.init(keystore, PostLocationServer.KeystorePass.toCharArray());

		TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
		tmf.init(trustStore);

		sslContext = SSLContext.getInstance("TLS");
		TrustManager[] trustManagers = tmf.getTrustManagers();
		sslContext.init(kmf.getKeyManagers(), trustManagers, null);
		this.logUtilsMain.writeLog(PostLocationServer.placeholder + "SSL-Encryption OK.");
	}



	private class UnfreezeConnections extends TimerTask
	{
		public void run()
		{
			synchronized(connectionMap)
			{
				if(connectionMap.size() != 0)
				{
					logUtilsMain.writeLog(PostLocationServer.placeholder + "Clearing IP-HASHMAP");
					connectionMap.clear();
				}
			}
		}
	}



	private static final int MAX_CONN_PER_MINUTE = 250;

	@Override
	public void run()
	{
		while(true)
		{
			try
			{
				SSLSocket socket = (SSLSocket) serverSocket.accept();
				if(this.connectionMap.get(socket.getInetAddress().toString()) != null)
				{
					if(this.connectionMap.get(socket.getInetAddress().toString()) >= PostLocationServer.MAX_CONN_PER_MINUTE)
					{
						socket.close();
					}
					else
					{
						this.connectionMap.put(socket.getInetAddress().toString(),  this.connectionMap.get(socket.getInetAddress().toString()) + 1);
						this.logUtilsMain.writeLog("Connection: " + socket.getInetAddress());

						PostLocationServer.executorMainThread.submit(new RequestHandler(socket));
					}
				}
				else
				{
					this.connectionMap.put(socket.getInetAddress().toString(), 1);
					this.logUtilsMain.writeLog("Connection: " + socket.getInetAddress());
					PostLocationServer.executorMainThread.submit(new RequestHandler(socket));
				}
			}
			catch(Exception ec)
			{
				this.logUtilsMain.writeLog("InforamtionServer(ACCEPT_ERROR): " + ec);
			}
		}
	}

	private static final String cmd_PostPhoto = "PLPNB";
	private static final String cmd_PostVideo = "PLPNV";
	private static final String cmd_Follow = "PLFS"; // OLD: PLWN
	private static final String cmd_deleteAccount = "LRRA";
	private static final String cmd_markChatAsReaded = "PLUOC";
	private static final String cmd_searchingPerson = "PUGHP";
	private static final String cmd_searchingHashtag = "PUGHH";
	private static final String cmd_getRawDataUserByPidImage = "PLUPP";
	private static final String cmd_getRawDataUserByPidVideo = "PLUPV";
	private static final String cmd_saveOrUnsaveSinglePostInPrivate = "PLSPP";
	private static final String cmd_setPostWasSeen = "PLSPU";
	private static final String cmd_deletePostFromPrivateUser = "PLRPP";
	private static final String cmd_blockFriend = "PLBF";
	private static final String cmd_DeclineFriendAnfrage = "PLDFA";
	private static final String cmd_AllowOrDisallowToSeeOwnPost = "PLGFIB";
	private static final String cmd_SynchAllPrivateChatPartnerMoments = "PLSCPM";
	private static final String cmd_SynchAllPrivateAndMomentsPostsUntilCertainTimeForSingleUser = "PLSPMUCT";
	private static final String cmd_SendTextMessage = "PLSPTM";
	private static final String cmd_UserTypingAMessage = "PLUTM";
	private static final String cmd_UserStoppedTypingAMessage = "PLUSTM";
	private static final String cmd_SendAudio = "PLSNA";
	private static final String cmd_DownloadAudio = "PLDAF";
	private static final String cmd_SendSticker = "PLSS";
	private static final String cmd_DownloadSticker = "PLDS";
	private static final String cmd_SyncStickerPacks = "PLSOSP";
	private static final String cmd_SendEmojieMessage = "PLSEM"; //For big emojies.
	private static final String cmd_SharePost = "PLSP";
	private static final String cmd_SynchHashtags = "PLSAH";
	private static final String cmd_loadMorePublicPosts = "PLLMPP";
	private static final String cmd_PostComment = "PLPCMT";
	private static final String cmd_SavePublicPost = "PLSPPOU";
	private static final String cmd_SharePublicPost = "PLSHPP";
	private static final String cmd_UploadProfilbild = "PLUPB";
	private static final String cmd_DownloadProfilbild = "PLGPB";
	private static final String cmd_DeleteProfilbild = "PLDPB";


	private static final String queryInsertFollowAnfrage = "INSERT INTO Following (UID_FOLLOWS, FUID_FOLLOWING, VALID) values (?, ?, ?)";
	private static final String queryAcceptFollowAnfrage = "UPDATE Following SET VALID=1 WHERE UID_FOLLOWS=? AND FUID_FOLLOWING=?";


	private static final String reply_error = "PLPERR";

	private static final String queryInsertNewPrivateUserPost = "INSERT INTO PrivateMoments (UID, Beschreibung, PID, TYPE) VALUES (?, ?, ?, ?)";
	private static final String queryInsertPostReceiver = "INSERT INTO PrivateReceivers (PPID, UID_REC) VALUES (?, ?)";

	private static final String queryIsPostSavedByUser = "SELECT * FROM PrivateMomentsSaved WHERE UID_POST_FROM=? AND UID_SAVED=? AND PPID=?";
	private static final String queryLookUpIfPostSavedWith24HoursCheck = "SELECT * FROM PrivateMoments WHERE PPID=? AND TIME <= DATE_SUB(NOW(), INTERVAL 24 HOUR) AND NOT EXISTS(SELECT NULL FROM PrivateMomentsSaved WHERE PrivateMoments.PPID = PrivateMomentsSaved.PPID)";
	private static final String queryGetSaversFromPrivatePost = "SELECT * FROM PrivateMomentsSaved WHERE PPID=?";

	private static final String queryRemoveFromSavedPosts = "DELETE FROM PrivateMomentsSaved WHERE UID_SAVED=? AND PPID=?";
	private static final String queryInsertPostSaved = "INSERT INTO PrivateMomentsSaved (UID_POST_FROM, UID_SAVED, PPID) values (?, ?, ?)";

	private static final String queryLookUpUsername = "Select Benutzername FROM Users WHERE UID=? LIMIT 1";

	private static final String queryGetUserPrivatePostOnlyPOSTID = "SELECT * FROM PrivateMoments WHERE PPID=? AND UID=? LIMIT 1";

	private static final String queryGetUserPrivatePostInChatContext = "SELECT * FROM (SELECT * FROM PrivateMoments WHERE PPID=?) AS P JOIN PrivateReceivers ON P.PPID=PrivateReceivers.PPID AND PrivateReceivers.UID_REC=? LIMIT 1";
	private static final String queryInsertNewHashtags = "INSERT INTO TAGS (PPID, UID_TAGER, TAG_NAME) values (?, ?, ?)";

	private static final String queryUpdateMessageStatusPrivateSeen = "UPDATE PrivateReceivers SET State=2 WHERE UID_REC=? AND PPID=?";
	private static final String queryDeletePrivatePostComplete = "DELETE FROM PrivateMoments WHERE UID=? AND PPID=?";

	private static final String queryGetReceiverCount = "SELECT COUNT(*) FROM PrivateReceivers WHERE PPID=?";

	private static final String queryDeleteReceiverFromPost = "DELETE FROM PrivateReceivers WHERE UID_REC=? AND PPID=?";

	private static final String queryDeleteSaveEintragAll = "DELETE FROM PrivateMomentsSaved WHERE UID_POST_FROM=? AND PPID=?";
	private static final String queryDeleteSavedFromPartner = "DELETE FROM PrivateMomentsSaved WHERE (UID_SAVED=? AND PPID=?) OR (UID_SAVED=? AND PPID=?)";

	private static final String queryGetHashtagsFromPost = "SELECT TAG_NAME FROM TAGS WHERE PPID=?";

	private static final String queryDeclineWatcherAnfrageByIDs = "DELETE FROM Following WHERE UID_FOLLOWS=? AND FUID_FOLLOWING=? LIMIT 1";
	private static final String queryRemoveFriendShip = "UPDATE Watcher SET WF=1 WHERE UID=? AND FUID=? OR UID=? AND FUID=?";
	private static final String querySetFriendShipTrueAgain = "UPDATE Watcher SET WF=0 WHERE UID=? AND FUID=? OR UID=? AND FUID=?";
	private static final String queryRemoveBlocked = "DELETE FROM BlockedUsers WHERE UID_BLOCKER=? AND UID_BLOCKED=?";
	private static final String queryGetVorname = "Select Vorname FROM Users WHERE UID=? LIMIT 1";
	private static final String queryInsertWatcher = "INSERT INTO Watcher (UID, FUID) values (?, ?)";
	private static final String queryInsertUserBlocked = "INSERT INTO BlockedUsers (UID_BLOCKER, UID_BLOCKED) values (?, ?)";
	private static final String queryGetVornameAndRegion = "SELECT Region, Vorname FROM Users WHERE UID=? LIMIT 1";

	private static final String queryGetAllPostsBetweenUsersForBlocking = "SELECT PPID, PID FROM PrivateMoments WHERE UID=? AND FUID=? GROUP BY PID LIMIT ?,50";
	private static final String queryDeleteAllMyPrivatePostsBetweenPartner = "DELETE FROM PrivateMoments WHERE UID=? AND FUID=?";

	private static final String queryGetAllFriends = "SELECT UID, FUID FROM Watcher WHERE UID=? OR FUID=?";
	private static final String queryGetAllPrivatePostsForDeletingAccount = "SELECT PID FROM PrivateMoments WHERE UID=? GROUP BY PID LIMIT ?, 100"; //For deleting it
	private static final String queryGetFullProfil = "SELECT * FROM Users WHERE UID=? LIMIT 1";

	//DELETINGS

	private static final String queryMarkFriendShipAsAccountDeleted = "UPDATE Watcher SET AD=1 WHERE UID=? OR FUID=?";
	private static final String querySetAccountToDeleted = "UPDATE Users SET Deleted=1 WHERE UID=?";
	private static final String queryDeleteBlockedShips = "DELETE FROM BlockedUsers WHERE UID_BLOCKER=? OR UID_BLOCKED=?";
	private static final String queryDeleteFCMToken = "DELETE FROM FirebaseCloudMessaging WHERE UID=?";
	private static final String queryDeleteSession = "DELETE FROM Sessions WHERE UID=?";
	private static final String queryDeleteAllFollowingAnfragen = "DELETE FROM Following WHERE UID_FOLLOWS=? OR FUID_FOLLOWING=?";
	private static final String queryDeleteAllMyServerMessages = "DELETE FROM Messages WHERE UID_RECEIVER=?";
	private static final String queryDeleteAllMyPrivatePosts = "DELETE FROM PrivateMoments WHERE UID=?";
	private static final String queryDeleteAllSavedPrivatePosts = "DELETE FROM PrivateMomentsSaved WHERE UID_POST_FROM=?";

	private static final String queryDeletePrivateSendUserPost = "DELETE FROM PrivateMoments WHERE PPID=?";


	private static final String qualityPrefixHigh = "HQ";

	public class RequestHandler extends Thread
	{
		private LogUtilsEsaph logUtilsRequest;
		private JSONObject jsonMessage;
		private SSLSocket socket;
		private PrintWriter writer;
		private BufferedReader reader;
		private Connection connection;
		private long ThreadUID;

		private RequestHandler(SSLSocket socket)
		{
			this.socket = socket;
		}

		public PrintWriter getWriter()
		{
			return this.writer;
		}

		public SSLSocket getSocket()
		{
			return this.socket;
		}

		public void returnConnectionToPool()
		{
			this.connection = pool.returnConnectionToPool(this.connection);
		}

        public String lookUpUsername(long UID) throws SQLException
        {
			PreparedStatement pr = null;
			ResultSet result = null;

        	try
			{
				pr = (PreparedStatement) this.connection
						.prepareStatement(PostLocationServer.queryLookUpUsername);
				pr.setLong(1, UID);
				result = pr.executeQuery();

				String Username = null;

				if (result.next())
				{
					Username = result.getString("Benutzername");
				}

				if(Username == null)
				{
					throw new SQLException("Benutzername nicht gefunden");
				}

				return Username;
			}
        	catch (Exception ec)
			{
				return null;
			}
        	finally
			{
				if(pr != null)
				{
					pr.close();
				}

				if(result != null)
				{
					result.close();
				}
			}
        }

		@Override
		public void run()
		{
			try
			{
				this.logUtilsRequest = new LogUtilsEsaph(new File(PostLocationServer.mainServerLogPath),
						PostLocationServer.ServerType,
						socket.getInetAddress().getHostAddress(), -1);

				this.socket.setSoTimeout(15000);
				this.writer = new PrintWriter(new OutputStreamWriter(this.socket.getOutputStream(), StandardCharsets.UTF_8), true);
				this.reader = new BufferedReader(new InputStreamReader(this.socket.getInputStream(), StandardCharsets.UTF_8));
				this.jsonMessage = new JSONObject(this.readDataCarefully(5000));
				this.getConnectionToSql();


				if(checkSID())
				{
					this.logUtilsRequest.setUID(this.ThreadUID);
					String anfrage = this.jsonMessage.getString("PLSC");
					this.logUtilsRequest.writeLog("ANFRAGE: " + anfrage);


					if(anfrage.equals(PostLocationServer.cmd_PostPhoto)) //Photo posting
					{
						this.connection = pool.returnConnectionToPool(this.connection);
						String PID_LOCATION = null;
						File fileOnlyPrivatePersonHQ = null;
						File fileUploadCacheIntern = null;

						try
						{
							EsaphStoringHandler esaphStoringHandler = new EsaphStoringHandler();
							long timestampUploaded = System.currentTimeMillis();
							PID_LOCATION = this.generatePID(this.ThreadUID); //PID kann sich wiederholen, aber nur um den gleichen post zu identifizieren, um aus einer gruppe einen bestimmten post herauszufilter wird noch die miid ben�tigt.
							fileUploadCacheIntern = esaphStoringHandler.getTempFile(EsaphDataPrefix.JPG_PREFIX, this.ThreadUID); //datei f�r cache erzeugt.


							if(this.uploadFoto(fileUploadCacheIntern))
							{
								this.getConnectionToSql();
								JSONArray mainArray = this.jsonMessage.getJSONArray("WAMP");
								String Beschreibung = "";

								if(this.jsonMessage.has("DES"))
								{
									Beschreibung = this.jsonMessage.getString("DES");
								}

								JSONArray jsonArrayHashtags = this.jsonMessage.getJSONArray("ARR_EHT");
								long ID = -1;

								boolean everthingOk = true;

								JSONArray checkFirstPrivateSending = mainArray.getJSONArray(0);
								if(checkFirstPrivateSending.length() > 0
										&& Beschreibung.length() <= EsaphMaxSizes.SIZE_MAX_BESCHREIBUNG_LENGTH
										&& this.isHashtagArrayValid(jsonArrayHashtags))
								{
									this.logUtilsRequest.writeLog("Nur an Private personen schicken.");
									fileOnlyPrivatePersonHQ = esaphStoringHandler.getStoringFile(PID_LOCATION, EsaphStoragePaths.PATH_PRIVATE_UPLOADS);

									boolean moved = false;

									PreparedStatement prepareStatementInserPrivatePost =
											(PreparedStatement) this.connection.prepareStatement(PostLocationServer.queryInsertNewPrivateUserPost);

									prepareStatementInserPrivatePost.setLong(1, ThreadUID);
									prepareStatementInserPrivatePost.setString(2, Beschreibung);
									prepareStatementInserPrivatePost.setString(3, PID_LOCATION);
									prepareStatementInserPrivatePost.setShort(4, CMTypes.FPIC);
									prepareStatementInserPrivatePost.executeUpdate();

									try (ResultSet generatedKeys = prepareStatementInserPrivatePost.getGeneratedKeys())
									{
										if (generatedKeys.next())
										{
											ID = generatedKeys.getLong(1);
										}
										else {
											throw new SQLException("Creating post image id failed, no ID obtained.");
										}
									}
									finally
									{
										prepareStatementInserPrivatePost.close();
									}


									for(int counter = 0; counter < checkFirstPrivateSending.length(); counter++)
									{
										JSONObject cached = checkFirstPrivateSending.getJSONObject(counter);
										long fuid = cached.getLong("REC_ID");
										System.out.println("Maja debug: ID: " + fuid);
										if(ServerPolicy.isAllowed(connection, this.ThreadUID, fuid))
										{
											System.out.println("Maja debug: Is allowed");
											PreparedStatement preparedStatementInsertReceiver = (PreparedStatement)
													this.connection.prepareStatement(PostLocationServer.queryInsertPostReceiver);
											preparedStatementInsertReceiver.setLong(1, ID);
											preparedStatementInsertReceiver.setLong(2, fuid);
											int result = preparedStatementInsertReceiver.executeUpdate();
											preparedStatementInsertReceiver.close();
											System.out.println("Maja debug: result = " + result + " With id " + ID);
										}
										else
										{
											System.out.println("Maja debug: Not allowed");
											everthingOk = false;
										}

										if(!moved)
										{
											FileUtils.copyFile(fileUploadCacheIntern, fileOnlyPrivatePersonHQ);
											moved = true;
										}
									}
								}

								for(int counterHashtags = 0; counterHashtags < jsonArrayHashtags.length(); counterHashtags++)
								{
									JSONObject jsonObject = jsonArrayHashtags.getJSONObject(counterHashtags);
									PreparedStatement preparedAddHashtagsForPost = (PreparedStatement) this.connection.prepareStatement(PostLocationServer.queryInsertNewHashtags);
									preparedAddHashtagsForPost.setLong(1, ID);
									preparedAddHashtagsForPost.setLong(2, this.ThreadUID);
									preparedAddHashtagsForPost.setString(3, jsonObject.getString("TAG"));
									preparedAddHashtagsForPost.executeUpdate();
									preparedAddHashtagsForPost.close();
								}

								if(everthingOk)
								{
									this.logUtilsRequest.writeLog(PostLocationServer.placeholder + " post alles in ordnung.");

									JSONObject jsonObjectReply = new JSONObject();
									jsonObjectReply.put("PID", PID_LOCATION);
									jsonObjectReply.put("PPID", ID);
									this.writer.println(jsonObjectReply.toString());

									PostLocationServer.executorSubThreads.submit(new SendPictureToUser(this.ThreadUID,
											mainArray,
											jsonArrayHashtags,
											PID_LOCATION,
											ID,
											CMTypes.FPIC,
											Beschreibung,
											timestampUploaded,
											this.logUtilsRequest));
								}
								else
								{
									this.logUtilsRequest.writeLog(PostLocationServer.placeholder + " post nicht in ordnung l�sche...");
									JSONObject jsonObjectReply = new JSONObject();
									jsonObjectReply.put("ERR", PostLocationServer.reply_error);
									this.writer.println(jsonObjectReply.toString());
									throw new Exception("Post daten nicht in Ordnung.");
								}
							}
							else
							{
								//FEHLER BILD STIMMT NICHT.

								JSONObject jsonObjectReply = new JSONObject();
								jsonObjectReply.put("ERR", PostLocationServer.reply_error);
								this.writer.println(jsonObjectReply.toString());
							}
						}
						catch(Exception ec)
						{
							JSONObject jsonObjectReply = new JSONObject();
							jsonObjectReply.put("ERR", PostLocationServer.reply_error);
							this.writer.println(jsonObjectReply.toString());

							if(fileOnlyPrivatePersonHQ != null)
							{
								fileOnlyPrivatePersonHQ.delete();
							}

							this.logUtilsRequest.writeLog(PostLocationServer.placeholder + "Photo Posting error: " + ec);

							if(this.connection == null)
							{
								this.getConnectionToSql();
							}


							try
							{
								PreparedStatement prDeletePost = (PreparedStatement) this.connection.prepareStatement(PostLocationServer.queryDeletePrivateSendUserPost);
								prDeletePost.setString(1, PID_LOCATION);
								prDeletePost.executeUpdate();
								prDeletePost.close();
							}
							catch(Exception ecFirst)
							{
								this.logUtilsRequest.writeLog(PostLocationServer.placeholder + "fatal error, exception in exception by (Private Moments): " + ecFirst);
							}

							this.logUtilsRequest.writeLog(PostLocationServer.placeholder + "Restdaten Wurden erfolgreich gel�scht.");
						}
						finally
						{
							if(fileUploadCacheIntern != null) //nur ein mal l�schen. :) =) :D
							{
								fileUploadCacheIntern.delete();
							}
						}
					}


					else if(anfrage.equals(PostLocationServer.cmd_PostVideo))
					{
						this.connection = pool.returnConnectionToPool(this.connection);
						String PID_LOCATION = null;
						File fileOnlyPrivatePersonHQ = null;
						File fileUploadCacheIntern = null;

						try
						{
							EsaphStoringHandler esaphStoringHandler = new EsaphStoringHandler();
							long timestampUploaded = System.currentTimeMillis();
							PID_LOCATION = this.generatePID(this.ThreadUID); //PID kann sich wiederholen, aber nur um den gleichen post zu identifizieren, um aus einer gruppe einen bestimmten post herauszufilter wird noch die miid ben�tigt.
							fileUploadCacheIntern = esaphStoringHandler.getTempFile(EsaphDataPrefix.MP4_PREFIX, this.ThreadUID); //datei f�r cache erzeugt.

							if(this.uploadVideo(fileUploadCacheIntern))
							{
								this.getConnectionToSql();
								JSONArray mainArray = this.jsonMessage.getJSONArray("WAMP");

								String Beschreibung = "";

								if(this.jsonMessage.has("DES"))
								{
									Beschreibung = this.jsonMessage.getString("DES");
								}

								long ID = -1;
								boolean everthingOk = true;

								JSONArray jsonArrayHashtags = this.jsonMessage.getJSONArray("ARR_EHT");

								JSONArray checkFirstPrivateSending = mainArray.getJSONArray(0);
								if(checkFirstPrivateSending.length() > 0
										&& Beschreibung.length() <= EsaphMaxSizes.SIZE_MAX_BESCHREIBUNG_LENGTH
										&& this.isHashtagArrayValid(jsonArrayHashtags))
								{
									this.logUtilsRequest.writeLog("Nur an Private personen schicken.");
									fileOnlyPrivatePersonHQ = esaphStoringHandler.getStoringFile(PID_LOCATION, EsaphStoragePaths.PATH_PRIVATE_UPLOADS);

									PreparedStatement prInsertNewUserPost = (PreparedStatement) this.connection.prepareStatement(PostLocationServer.queryInsertNewPrivateUserPost);
									prInsertNewUserPost.setLong(1, this.ThreadUID);
									prInsertNewUserPost.setString(2, Beschreibung);
									prInsertNewUserPost.setString(3, PID_LOCATION);
									prInsertNewUserPost.setShort(4, CMTypes.FVID);
									prInsertNewUserPost.executeUpdate();

									try (ResultSet generatedKeys = prInsertNewUserPost.getGeneratedKeys())
									{
										if (generatedKeys.next())
										{
											ID = generatedKeys.getLong(1);
										}
										else {
											throw new SQLException("Creating post video id failed, no ID obtained.");
										}
									}
									finally
									{
										prInsertNewUserPost.close();
									}

									boolean moved = false;
									for(int counter = 0; counter < checkFirstPrivateSending.length(); counter++)
									{
										JSONObject cached = checkFirstPrivateSending.getJSONObject(counter);
										long fuid = cached.getLong("REC_ID");
										if(ServerPolicy.isAllowed(connection, this.ThreadUID, fuid))
										{
											PreparedStatement preparedStatementInsertReceiver = (PreparedStatement)
													this.connection.prepareStatement(PostLocationServer.queryInsertPostReceiver);
											preparedStatementInsertReceiver.setLong(1, ID);
											preparedStatementInsertReceiver.setLong(2, fuid);
											preparedStatementInsertReceiver.executeUpdate();
											preparedStatementInsertReceiver.close();
										}
										else
										{
											everthingOk = false;
										}

										if(!moved)
										{
											FileUtils.copyFile(fileUploadCacheIntern, fileOnlyPrivatePersonHQ);
											moved = true;
										}
									}

									for(int counterHashtags = 0; counterHashtags < jsonArrayHashtags.length(); counterHashtags++)
									{
										JSONObject jsonObject = jsonArrayHashtags.getJSONObject(counterHashtags);
										PreparedStatement preparedAddHashtagsForPost = (PreparedStatement) this.connection.prepareStatement(PostLocationServer.queryInsertNewHashtags);
										preparedAddHashtagsForPost.setLong(1, ID);
										preparedAddHashtagsForPost.setLong(2, this.ThreadUID);
										preparedAddHashtagsForPost.setString(3, jsonObject.getString("TAG"));
										preparedAddHashtagsForPost.executeUpdate();
										preparedAddHashtagsForPost.close();
									}
								}

								if(everthingOk)
								{
									this.logUtilsRequest.writeLog(PostLocationServer.placeholder + "Video-Post alles in ordnung.");
									JSONObject jsonObjectReply = new JSONObject();
									jsonObjectReply.put("PID", PID_LOCATION);
									jsonObjectReply.put("PPID", ID);

									this.writer.println(jsonObjectReply.toString());
									PostLocationServer.executorSubThreads.submit(new SendPictureToUser(this.ThreadUID,
											mainArray,
											jsonArrayHashtags,
											PID_LOCATION,
											ID,
											CMTypes.FVID,
											Beschreibung,
											timestampUploaded,
											this.logUtilsRequest));
								}
								else
								{
									this.logUtilsRequest.writeLog(PostLocationServer.placeholder + " post nicht in ordnung l�sche (Video)...");

									JSONObject jsonObjectReply = new JSONObject();
									jsonObjectReply.put("ERR", PostLocationServer.reply_error);
									this.writer.println(jsonObjectReply.toString());
									throw new Exception("Post daten nicht in Ordnung(Video).");
								}
							}
							else
							{
								//FEHLER BILD STIMMT NICHT.
								if(fileUploadCacheIntern != null)
								{
									fileUploadCacheIntern.delete();
								}
								this.writer.println(PostLocationServer.reply_error);
							}
						}
						catch(Exception ec)
						{
							JSONObject jsonObjectReply = new JSONObject();
							jsonObjectReply.put("ERR", PostLocationServer.reply_error);
							this.writer.println(jsonObjectReply.toString());

							if(fileOnlyPrivatePersonHQ != null)
							{
								fileOnlyPrivatePersonHQ.delete();
							}

							this.logUtilsRequest.writeLog(PostLocationServer.placeholder + "Video Posting error: " + ec);

							if(this.connection == null)
							{
								this.getConnectionToSql();
							}

							try
							{
								PreparedStatement prDeletePost = (PreparedStatement) this.connection.prepareStatement(PostLocationServer.queryDeletePrivateSendUserPost);
								prDeletePost.setString(1, PID_LOCATION);
								prDeletePost.executeUpdate();
								prDeletePost.close();
							}
							catch(Exception ecFirst)
							{
								this.logUtilsRequest.writeLog(PostLocationServer.placeholder + "fatal error, exception in exception by (Private Moments / Video): " + ecFirst);
							}

							this.logUtilsRequest.writeLog(PostLocationServer.placeholder + "Restdaten Wurden erfolgreich gel�scht. (Video)");
						}
						finally
						{
							if(fileUploadCacheIntern != null) //nur ein mal l�schen. :) =) :D
							{
								fileUploadCacheIntern.delete();
							}
						}
					}


					else if(anfrage.equals(PostLocationServer.cmd_blockFriend))
					{
						long puid = this.jsonMessage.getLong("FUSRN");
						if(puid > -1)
						{
							if(this.jsonMessage.getInt("ERA") == 1) //DELETE ALL IN PRIVATE.
							{
								int startFrom = 0;

								do
								{
									PreparedStatement prGetAllPrivateUserPosts = (PreparedStatement) //ALLE Beitr�ge zwischen mir wo ich absender bin.
											this.connection.prepareStatement(PostLocationServer.queryGetAllPostsBetweenUsersForBlocking);
									prGetAllPrivateUserPosts.setLong(1, this.ThreadUID);
									prGetAllPrivateUserPosts.setLong(2, puid);
									prGetAllPrivateUserPosts.setInt(3, startFrom);
									ResultSet result = prGetAllPrivateUserPosts.executeQuery();

									if(result.next())
									{
										do
										{
											EsaphStoringHandler esaphStoringHandler = new EsaphStoringHandler();
											File fileHQ = esaphStoringHandler.getStoringFile(result.getString("PID"), EsaphStoragePaths.PATH_PRIVATE_UPLOADS);

											if(fileHQ != null)
											{
												fileHQ.delete();
											}

											PreparedStatement queryGetAllPrivatePostingsSavedFromConversation = //L�sche alle sachen die gespeichert wurden von mir oder partner.
													(PreparedStatement) this.connection.prepareStatement(PostLocationServer.queryDeleteSavedFromPartner);
											queryGetAllPrivatePostingsSavedFromConversation.setLong(1, puid);
											queryGetAllPrivatePostingsSavedFromConversation.setLong(2, result.getLong("PPID"));
											queryGetAllPrivatePostingsSavedFromConversation.setLong(3, this.ThreadUID);
											queryGetAllPrivatePostingsSavedFromConversation.setLong(4, result.getLong("PPID"));
											queryGetAllPrivatePostingsSavedFromConversation.executeUpdate();
											queryGetAllPrivatePostingsSavedFromConversation.close();

											startFrom++;
										}
										while(result.next());
									}
									else
									{
										startFrom = -1;
									}

									prGetAllPrivateUserPosts.close();
									result.close();
								}
								while(startFrom > 0);

								PreparedStatement queryDeleteAllMyPosts = //Lösche alle geschickten bilder aus datenbank.
										(PreparedStatement) this.connection.prepareStatement(PostLocationServer.queryDeleteAllMyPrivatePostsBetweenPartner);
								queryDeleteAllMyPosts.setLong(1, this.ThreadUID);
								queryDeleteAllMyPosts.setLong(2, puid);
								queryDeleteAllMyPosts.executeUpdate();
								queryDeleteAllMyPosts.close();
							}

							PreparedStatement blockUser = (PreparedStatement) this.connection.prepareStatement(PostLocationServer.queryInsertUserBlocked);
							blockUser.setLong(1, this.ThreadUID);
							blockUser.setLong(2, puid);
							blockUser.executeUpdate();
							blockUser.close();
							this.DeclineOneFollowShipByIDS(this.ThreadUID, puid);


							PreparedStatement prRemoveWatchShip = (PreparedStatement) this.connection.prepareStatement(PostLocationServer.queryRemoveFriendShip);
							prRemoveWatchShip.setLong(1, this.ThreadUID);
							prRemoveWatchShip.setLong(2, puid);
							prRemoveWatchShip.setLong(3, puid);
							prRemoveWatchShip.setLong(4, this.ThreadUID);
							prRemoveWatchShip.executeUpdate();
							prRemoveWatchShip.close();


							this.writer.println("1");
						}
						else
						{
							this.writer.println("0");
						}
					}

					else if(anfrage.equals(PostLocationServer.cmd_DeclineFriendAnfrage))
					{
						long fuid = this.jsonMessage.getLong("FUSRN");
						if(fuid > -1)
						{
							this.DeclineOneFollowShipByIDS(fuid, this.ThreadUID);
							String result = ""+ServerPolicy.getFriendshipState(connection, this.ThreadUID, fuid);

							EsaphInternalMessageCreator json = new EsaphInternalMessageCreator(MessageTypeIdentifier.CMD_FriendStatus,
									fuid);

							short FRIEND_STATUS = ServerPolicy.getFriendshipState(connection, fuid, this.ThreadUID);
							json.putInto("USRN", this.ThreadUID);
							json.putInto("FST", FRIEND_STATUS);
							json.putInto("USRN_STR", this.lookUpUsername(this.ThreadUID));

							PostLocationServer.executorSubThreads.submit(new SendInformationToUser(json.getJSON(), this.logUtilsRequest, PostLocationServer.this));
							this.writer.println(result);
						}
						else
						{
							this.writer.println("0");
						}
					}

					else if(anfrage.equals(PostLocationServer.cmd_Follow))
					{
						long partnerUID = this.jsonMessage.getLong("FUSRN");

						if(partnerUID != this.ThreadUID && partnerUID > -1)
						{
							short checkFirst = ServerPolicy.getFriendshipState(connection, this.ThreadUID, partnerUID);

							System.out.println("FOLLOW STATE: " + checkFirst + " CALLER=" + this.ThreadUID);

							if(checkFirst != ServerPolicy.POLICY_DETAIL_CASE_I_BLOCKED_SOMEONE && checkFirst != ServerPolicy.POLICY_DETAIL_CASE_I_WAS_BLOCKED) //Blockiert, abbruch
							{
								if(checkFirst == ServerPolicy.POLICY_DETAIL_CASE_NOTHING || checkFirst == ServerPolicy.POLICY_DETAIL_FOLLOWS_ME) //Follow user
								{
									PreparedStatement prInsertWatcher = (PreparedStatement) this.connection.prepareStatement(PostLocationServer.queryInsertFollowAnfrage);
									prInsertWatcher.setLong(1, this.ThreadUID); //Der hat die Anfrage abgeschickt.
									prInsertWatcher.setLong(2, partnerUID); //Er hat die Anfrage bekommen.
									prInsertWatcher.setShort(3, (short) 0);
									prInsertWatcher.executeUpdate();
									prInsertWatcher.close();
								}
								else if(checkFirst == ServerPolicy.POLICY_DETAIL_CASE_I_SENT_ANFRAGE) //Anfrage zur�ckziehen.
								{
									this.DeclineOneFollowShipByIDS(this.ThreadUID, partnerUID);
								}
								else if(checkFirst == ServerPolicy.POLICY_DETAIL_CASE_I_WAS_ANGEFRAGT)
								{
									PreparedStatement preparedStatementSetAccepted = (PreparedStatement) this.connection.prepareStatement(PostLocationServer.queryAcceptFollowAnfrage);
									preparedStatementSetAccepted.setLong(1, partnerUID); //Er folgt mir.
									preparedStatementSetAccepted.setLong(2, this.ThreadUID); //Mir wird gefolgt
									preparedStatementSetAccepted.executeUpdate();
									preparedStatementSetAccepted.close();

									short PARTNER_STATE = ServerPolicy.getFriendshipState(this.connection, partnerUID, this.ThreadUID); //wenn partner auch folgt.
									short OWN_STATE = ServerPolicy.getFriendshipState(this.connection, this.ThreadUID, partnerUID); //wenn ich auch folge.
									if(PARTNER_STATE == ServerPolicy.POLICY_DETAIL_I_FOLLOW
											&& OWN_STATE == ServerPolicy.POLICY_DETAIL_I_FOLLOW) //Beide folgen nun einander, es wird ein Friendship erzeugt.
									{
										PreparedStatement prInsertWatcherUpdateStatus = (PreparedStatement) this.connection.prepareStatement(PostLocationServer.querySetFriendShipTrueAgain);
										prInsertWatcherUpdateStatus.setLong(1, this.ThreadUID);
										prInsertWatcherUpdateStatus.setLong(2, partnerUID);
										prInsertWatcherUpdateStatus.setLong(3, partnerUID);
										prInsertWatcherUpdateStatus.setLong(4, this.ThreadUID);
										int rowsUpdated = prInsertWatcherUpdateStatus.executeUpdate(); //Trying to update the state, if they was friends. So to not lose them pictures.
										prInsertWatcherUpdateStatus.close();

										if(rowsUpdated <= 0) //War kein "wasFriends", also wird ein neuer eintrag eingelegt.
										{
											PreparedStatement acceptWatcherBypass = (PreparedStatement) this.connection.prepareStatement(PostLocationServer.queryInsertWatcher);
											acceptWatcherBypass.setLong(1, partnerUID);
											acceptWatcherBypass.setLong(2, this.ThreadUID);
											acceptWatcherBypass.executeUpdate();
											acceptWatcherBypass.close();
										}

										this.DeclineOneFollowShipByIDS(this.ThreadUID, partnerUID);
										this.DeclineOneFollowShipByIDS(partnerUID, this.ThreadUID);
									}
								}
								else if(checkFirst == ServerPolicy.POLICY_DETAIL_I_FOLLOW)
								{
									this.DeclineOneFollowShipByIDS(this.ThreadUID, partnerUID);
								}
								else if(checkFirst == ServerPolicy.POLICY_DETAIL_CASE_FRIENDS) //Both are follwing, we are Friends.
								{
									PreparedStatement prRemoveWatchShip = (PreparedStatement) this.connection.prepareStatement(PostLocationServer.queryRemoveFriendShip);
									prRemoveWatchShip.setLong(1, this.ThreadUID);
									prRemoveWatchShip.setLong(2, partnerUID);
									prRemoveWatchShip.setLong(3, partnerUID);
									prRemoveWatchShip.setLong(4, this.ThreadUID);
									prRemoveWatchShip.executeUpdate();
									prRemoveWatchShip.close();
								}
							}
							else if(checkFirst == ServerPolicy.POLICY_DETAIL_CASE_I_BLOCKED_SOMEONE) //FREIGEBEN
							{
								this.logUtilsRequest.writeLog("Freigeben von nutzer.");
								PreparedStatement prRemoveBlocked = (PreparedStatement) this.connection.prepareStatement(PostLocationServer.queryRemoveBlocked);
								prRemoveBlocked.setLong(1, this.ThreadUID); //ICH MUSS DER BLOCKER SEIN.!!
								prRemoveBlocked.setLong(2, partnerUID);
								prRemoveBlocked.executeUpdate();
								prRemoveBlocked.close();
							}

							short FR_STATE = ServerPolicy.getFriendshipState(connection, this.ThreadUID, partnerUID);
							JSONObject jsonObject = new JSONObject();
							jsonObject.put("FRT", FR_STATE);

							EsaphInternalMessageCreator json = new EsaphInternalMessageCreator(MessageTypeIdentifier.CMD_FriendStatus,
									partnerUID);

							short FRIEND_STATUS = ServerPolicy.getFriendshipState(connection, partnerUID, this.ThreadUID);
							json.putInto("USRN", this.ThreadUID);
							json.putInto("FST", FRIEND_STATUS);
							json.putInto("USRN_STR", this.lookUpUsername(this.ThreadUID));

							PreparedStatement prLookUpPbPID = (PreparedStatement) this.connection.prepareStatement(PostLocationServer.queryGetVornameAndRegion);
							prLookUpPbPID.setLong(1, partnerUID);
							ResultSet resultSetPbLookUp = prLookUpPbPID.executeQuery();
							if(resultSetPbLookUp.next())
							{
								json.putInto("VORN", resultSetPbLookUp.getString("Vorname"));
								json.putInto("REG", resultSetPbLookUp.getString("Region"));
							}

							resultSetPbLookUp.close();
							prLookUpPbPID.close();



							System.out.println("FOLLOW STATE HANDLED: " + FR_STATE + " CALLER=" + this.ThreadUID);
							System.out.println("FOLLOW STATE HANDLED PARTNER: " + FRIEND_STATUS + " CALLER=" + partnerUID);

							if(FRIEND_STATUS == ServerPolicy.POLICY_DETAIL_CASE_FRIENDS)
							{
								PreparedStatement preparedStatementGetProfil = (PreparedStatement) this.connection
										.prepareStatement(PostLocationServer.queryGetFullProfil);
								preparedStatementGetProfil.setLong(1, this.ThreadUID);
								ResultSet result = preparedStatementGetProfil.executeQuery();
								if (result.next())
								{
									JSONObject singleFriend = new JSONObject();
									singleFriend.put("UID", result.getLong("UID"));
									singleFriend.put("Benutzername", result.getString("Benutzername"));
									singleFriend.put("Vorname", result.getString("Vorname"));
									singleFriend.put("Geburtstag", result.getTimestamp("Geburtstag").getTime());
									singleFriend.put("Region", result.getString("Region"));
									singleFriend.put("DESCPL", new JSONObject(result.getString("Description")));
									json.putInto("PF", singleFriend);
									jsonObject.put("USR", singleFriend);
								}
								preparedStatementGetProfil.close();
								result.close();
							}

							PostLocationServer.executorSubThreads.submit(new SendInformationToUser(json.getJSON(), this.logUtilsRequest, PostLocationServer.this));

							this.writer.println(jsonObject.toString());
						}
						else
						{
							this.logUtilsRequest.writeLog(PostLocationServer.placeholder + "user trying to like/follow his own post.");
						}
					}

					else if(anfrage.equals(PostLocationServer.cmd_deleteAccount))
					{
						PreparedStatement prGetAllFriends = (PreparedStatement) this.connection.prepareStatement(PostLocationServer.queryGetAllFriends);
						prGetAllFriends.setLong(1, this.ThreadUID);
						prGetAllFriends.setLong(2, this.ThreadUID);
						ResultSet resultGetAllFriends = prGetAllFriends.executeQuery();

						JSONArray jsonArrEmpf = new JSONArray();

						while(resultGetAllFriends.next())
						{
							JSONObject jsonObjectReceiver = new JSONObject();
							long uid = resultGetAllFriends.getLong("UID");
							long fuid = resultGetAllFriends.getLong("FUID");
							if(this.ThreadUID == uid)
							{
								jsonObjectReceiver.put("REC_ID", fuid);
							}
							else
							{
								jsonObjectReceiver.put("REC_ID", uid);
							}
							jsonArrEmpf.put(jsonObjectReceiver);
						}

						EsaphInternalMessageCreator json = new EsaphInternalMessageCreator(MessageTypeIdentifier.CMD_UserDeletedAccount,
								jsonArrEmpf);
						json.putInto("USRN", this.ThreadUID);

						PostLocationServer.executorSubThreads.submit(new SendInformationToUser(json.getJSON(), this.logUtilsRequest, PostLocationServer.this));

						int currentPosition = 0;
						while(currentPosition > -1)
						{
							PreparedStatement prGetAllPrivatePostsFromMe = (PreparedStatement) this.connection.prepareStatement(PostLocationServer.queryGetAllPrivatePostsForDeletingAccount,
									ResultSet.TYPE_SCROLL_INSENSITIVE,
									ResultSet.CONCUR_READ_ONLY);
							prGetAllPrivatePostsFromMe.setLong(1, this.ThreadUID);
							prGetAllPrivatePostsFromMe.setLong(2, currentPosition);

							ResultSet resultGetAllPrivatePosts = prGetAllPrivatePostsFromMe.executeQuery();

							if(resultGetAllPrivatePosts.next())
							{
								try
								{
									resultGetAllPrivatePosts.last();
									currentPosition = currentPosition + resultGetAllPrivatePosts.getRow();
									resultGetAllPrivatePosts.beforeFirst();
									resultGetAllPrivatePosts.next();
								}
								catch(Exception ex)
								{
									this.logUtilsRequest.writeLog(PostLocationServer.placeholder + "Failed get Row delete account: " + ex);
								}

								do
								{
									EsaphStoringHandler esaphStoringHandler = new EsaphStoringHandler();
									File fileHQ = esaphStoringHandler.getStoringFile(resultGetAllPrivatePosts.getString("PID"),
											EsaphStoragePaths.PATH_PRIVATE_UPLOADS);

									if(fileHQ != null)
									{
										fileHQ.delete();
									}
								}
								while(resultGetAllPrivatePosts.next());
							}
							else
							{
								currentPosition = -2;
							}

							prGetAllPrivatePostsFromMe.close();
							resultGetAllPrivatePosts.close();

						}

						PreparedStatement prDeleteAllPrivatePosts = (PreparedStatement) this.connection.prepareStatement(PostLocationServer.queryDeleteAllMyPrivatePosts);
						prDeleteAllPrivatePosts.setLong(1, this.ThreadUID);
						prDeleteAllPrivatePosts.executeUpdate();
						prDeleteAllPrivatePosts.close();

						PreparedStatement prDeleteSavedPrivatePosts = (PreparedStatement) this.connection.prepareStatement(PostLocationServer.queryDeleteAllSavedPrivatePosts);
						prDeleteSavedPrivatePosts.setLong(1, this.ThreadUID);
						prDeleteSavedPrivatePosts.executeUpdate();
						prDeleteSavedPrivatePosts.close();

						PreparedStatement prDeleteServerMessages = (PreparedStatement) this.connection.prepareStatement(PostLocationServer.queryDeleteAllMyServerMessages);
						prDeleteServerMessages.setLong(1, this.ThreadUID);
						prDeleteServerMessages.executeUpdate();
						prDeleteServerMessages.close();

						PreparedStatement prDeleteWatcherAnfragen = (PreparedStatement) this.connection.prepareStatement(PostLocationServer.queryDeleteAllFollowingAnfragen);
						prDeleteWatcherAnfragen.setLong(1, this.ThreadUID);
						prDeleteWatcherAnfragen.setLong(2, this.ThreadUID);
						prDeleteWatcherAnfragen.executeUpdate();
						prDeleteWatcherAnfragen.close();

						PreparedStatement prDeleteSession = (PreparedStatement) this.connection.prepareStatement(PostLocationServer.queryDeleteSession);
						prDeleteSession.setLong(1, this.ThreadUID);
						prDeleteSession.executeUpdate();
						prDeleteSession.close();

						PreparedStatement prMarkAsFriendShipNoVerficated = (PreparedStatement) this.connection.prepareStatement(PostLocationServer.queryMarkFriendShipAsAccountDeleted);
						prMarkAsFriendShipNoVerficated.setLong(1, this.ThreadUID);
						prMarkAsFriendShipNoVerficated.setLong(2, this.ThreadUID);
						prMarkAsFriendShipNoVerficated.executeUpdate();
						prMarkAsFriendShipNoVerficated.close();

						PreparedStatement prDeleteFCM = (PreparedStatement) this.connection.prepareStatement(PostLocationServer.queryDeleteFCMToken);
						prDeleteFCM.setLong(1, this.ThreadUID);
						prDeleteFCM.executeUpdate();
						prDeleteFCM.close();

						PreparedStatement prDeleteBlocked = (PreparedStatement) this.connection.prepareStatement(PostLocationServer.queryDeleteBlockedShips);
						prDeleteBlocked.setLong(1, this.ThreadUID);
						prDeleteBlocked.setLong(2, this.ThreadUID);
						prDeleteBlocked.executeUpdate();
						prDeleteBlocked.close();

						// TODO: 02.03.2019 delete public posts

						PreparedStatement prSetAccountDeleted = (PreparedStatement) this.connection.prepareStatement(PostLocationServer.querySetAccountToDeleted);
						prSetAccountDeleted.setLong(1, this.ThreadUID);
						prSetAccountDeleted.executeUpdate();
						prSetAccountDeleted.close();

						//Seen moment posts werden nicht gel�scht. da selbst wenn der account weg ist, sie ja trotzdem gesehen wurden falls eingetragen.
						this.logUtilsRequest.writeLog("Deleted Account: " + this.ThreadUID + "-" + this.ThreadUID + " goodbye. Thanks for using!");
						this.writer.println("1");
					}
					else if (anfrage.equals(PostLocationServer.cmd_searchingPerson)) //Suche nach Personen
					{
						new SearchPersons(PostLocationServer.this, this, this.logUtilsRequest).run();
					}
					else if(anfrage.equals(PostLocationServer.cmd_searchingHashtag))
					{
						new SearchHashtags(PostLocationServer.this, this, this.logUtilsRequest).run();
					}
					else if(anfrage.equals(PostLocationServer.cmd_getRawDataUserByPidImage))
					{
						EsaphStoringHandler esaphStoringHandler = new EsaphStoringHandler();
						File file = esaphStoringHandler.getStoringFile(this.jsonMessage.getString("PID"), EsaphStoragePaths.PATH_PRIVATE_UPLOADS);
						if(file != null)
						{
							if(this.jsonMessage.has("VW") && this.jsonMessage.has("VH"))
							{
								int viewsWidth = this.jsonMessage.getInt("VW");
								int viewsHeight = this.jsonMessage.getInt("VH");
								File sonderAnfertigung = EsaphImageScaler.esaphScaleImageForClient(file,
										esaphStoringHandler.getTempFile(EsaphDataPrefix.JPG_PREFIX, this.ThreadUID),
										new Dimension(viewsWidth, viewsHeight));

								if(sonderAnfertigung.exists())
								{
									this.sendImage(sonderAnfertigung);
									sonderAnfertigung.delete();
								}
							}
							else
							{
								this.sendImage(file);
							}
						}
					}
					else if(anfrage.equals(PostLocationServer.cmd_getRawDataUserByPidVideo))
					{
						EsaphStoringHandler esaphStoringHandler = new EsaphStoringHandler();
						File file = esaphStoringHandler.getStoringFile(this.jsonMessage.getString("PID"), EsaphStoragePaths.PATH_PRIVATE_UPLOADS);
						if(file.exists())
						{
							this.writer.println("1");
							if(this.readDataCarefully(1).equals("1"))
							{
								this.sendVideo(file);
							}
						}
					}
					else if(anfrage.equals(PostLocationServer.cmd_AllowOrDisallowToSeeOwnPost))
					{
						new AllowOrDisallowToSeeOwnPost(PostLocationServer.this, this, this.logUtilsRequest).run();
					}
					else if(anfrage.equals(PostLocationServer.cmd_saveOrUnsaveSinglePostInPrivate))
					{
						new SaveUnsaveSinglePostInPrivate(PostLocationServer.this, this, this.logUtilsRequest).run();
					}
					else if(anfrage.equals(PostLocationServer.cmd_setPostWasSeen))
					{
						long PPID = this.jsonMessage.getLong("PPID");
						long ABSENDERUID = this.jsonMessage.getLong("FUSRN");

						PreparedStatement pr = (PreparedStatement) this.connection.prepareStatement(PostLocationServer.queryUpdateMessageStatusPrivateSeen);
						pr.setLong(1, this.ThreadUID);
						pr.setLong(2, PPID);
						int updated = pr.executeUpdate();

						if(updated > 0)
						{
							this.writer.println("1");
							EsaphInternalMessageCreator jsonMessage = new EsaphInternalMessageCreator(MessageTypeIdentifier.CMD_UserSeenYourPostPrivate,
									ABSENDERUID);
							jsonMessage.putInto("USRN", this.ThreadUID);
							jsonMessage.putInto("PPID", PPID);
							PostLocationServer.executorSubThreads.submit(new SendInformationToUser(jsonMessage.getJSON(), this.logUtilsRequest, PostLocationServer.this));
						}
						else
						{
							this.writer.println("0");
						}

						pr.close();
					}
					else if(anfrage.equals(PostLocationServer.cmd_deletePostFromPrivateUser)) //delete my post completly
					{
						boolean deleted = false;
						long FUID_RECEIVER = this.jsonMessage.getLong("FUID");
						long PPID = this.jsonMessage.getLong("PPID");

						PreparedStatement preparedStatementDeleteReceiver = this.connection.prepareStatement(PostLocationServer.queryDeleteReceiverFromPost);
						preparedStatementDeleteReceiver.setLong(1, FUID_RECEIVER);
						preparedStatementDeleteReceiver.setLong(2, PPID);
						int updated = preparedStatementDeleteReceiver.executeUpdate();
						preparedStatementDeleteReceiver.close();

						if(updated > 0)
						{
							deleted = true;
							PreparedStatement prSaved = (PreparedStatement) this.connection.prepareStatement(PostLocationServer.queryDeleteSaveEintragAll);
							prSaved.setLong(1, this.ThreadUID);
							prSaved.setLong(2, this.jsonMessage.getLong("PPID"));
							prSaved.executeUpdate();
							prSaved.close();
						}


						PreparedStatement preparedStatementGetReceivers = this.connection.prepareStatement(PostLocationServer.queryGetReceiverCount);
						preparedStatementGetReceivers.setLong(1, PPID);
						ResultSet resultSetReceiverCount = preparedStatementGetReceivers.executeQuery();
						if(resultSetReceiverCount.next())
						{
							int countReceivers = resultSetReceiverCount.getInt("COUNT(*)");
							if(countReceivers <= 0)
							{

								PreparedStatement prGetPostPID = (PreparedStatement) this.connection.prepareStatement(PostLocationServer.queryGetUserPrivatePostOnlyPOSTID);
								prGetPostPID.setLong(1,this.jsonMessage.getLong("PPID"));
								prGetPostPID.setLong(2, this.ThreadUID);
								ResultSet resultGetPostPid = prGetPostPID.executeQuery();
								if(resultGetPostPid.next())
								{
									EsaphStoringHandler esaphStoringHandler = new EsaphStoringHandler();
									File fileHQ = esaphStoringHandler.getStoringFile(resultGetPostPid.getString("PID"), EsaphStoragePaths.PATH_PRIVATE_UPLOADS);

									if(fileHQ != null)
									{
										fileHQ.delete();
									}
								}
								prGetPostPID.close();
								resultGetPostPid.close();

								PreparedStatement pr = (PreparedStatement) this.connection.prepareStatement(PostLocationServer.queryDeletePrivatePostComplete);
								pr.setLong(1, this.ThreadUID); //Security checks if it is the post current user logged in. That no one can transmitte other pid to delete.
								pr.setLong(2, this.jsonMessage.getLong("PPID"));
								pr.executeUpdate();
								pr.close();
							}
						}

						preparedStatementGetReceivers.close();
						resultSetReceiverCount.close();

						if(deleted && FUID_RECEIVER > -1)
						{
							this.writer.println("1");

							EsaphInternalMessageCreator jsonMessage = new EsaphInternalMessageCreator(MessageTypeIdentifier.CMD_UserRemovedPostFromPrivate, FUID_RECEIVER);
							jsonMessage.putInto("USRN", this.ThreadUID);
							jsonMessage.putInto("PPID", PPID);
							PostLocationServer.executorSubThreads.submit(new SendInformationToUser(jsonMessage.getJSON(), this.logUtilsRequest, PostLocationServer.this));
						}
						else
						{
							this.writer.println("0");
						}
					}
					else if(anfrage.equals(PostLocationServer.cmd_SynchAllPrivateChatPartnerMoments))
					{
						new SynchAllPrivateChatPosts(PostLocationServer.this, this, this.logUtilsRequest).run();
					}
					else if(anfrage.equals(PostLocationServer.cmd_SynchAllPrivateAndMomentsPostsUntilCertainTimeForSingleUser))
					{
						new SynchAllPostsUntilCertainTimeForSingleUser(PostLocationServer.this, this, this.logUtilsRequest).run();
					}
					else if(anfrage.equals(PostLocationServer.cmd_SendTextMessage))
					{
						new SendTextMessage(PostLocationServer.this, this, this.logUtilsRequest).run();
					}
					else if(anfrage.equals(PostLocationServer.cmd_UserTypingAMessage))
					{
						new SendUserTyping(PostLocationServer.this, this, this.logUtilsRequest).run();
					}
					else if(anfrage.equals(PostLocationServer.cmd_UserStoppedTypingAMessage))
					{
						new SendUserStoppedTyping(PostLocationServer.this, this, this.logUtilsRequest).run();
					}
					else if(anfrage.equals(PostLocationServer.cmd_SendAudio))
					{
						new SendAudio(PostLocationServer.this, this, this.logUtilsRequest).run();
					}
					else if(anfrage.equals(PostLocationServer.cmd_DownloadAudio))
					{
						new DownloadAudioFile(PostLocationServer.this, this, this.logUtilsRequest, true).run();
					}
					else if(anfrage.equals(PostLocationServer.cmd_SendSticker))
					{
						new SendSticker(PostLocationServer.this, this, this.logUtilsRequest).run();
					}
					else if(anfrage.equals(PostLocationServer.cmd_DownloadSticker))
					{
						new DownloadSticker(PostLocationServer.this, this, this.logUtilsRequest).run();
					}
					else if(anfrage.equals(PostLocationServer.cmd_SyncStickerPacks))
					{
						new SyncStickerPacks(PostLocationServer.this, this, this.logUtilsRequest).run();
					}
					else if(anfrage.equals(PostLocationServer.cmd_SendEmojieMessage))
					{
						new SendEmojieMessage(PostLocationServer.this, this, this.logUtilsRequest).run();
					}
					else if(anfrage.equals(PostLocationServer.cmd_SharePost))
					{
						new SharePost(PostLocationServer.this, this, this.logUtilsRequest).run();
					}
					else if(anfrage.equals(PostLocationServer.cmd_SynchHashtags))
					{
						new SynchHashtags(PostLocationServer.this, this, this.logUtilsRequest).run();
					}
					else if(anfrage.equals(PostLocationServer.cmd_loadMorePublicPosts))
					{
						new FetchPublicPosts(PostLocationServer.this, this, this.logUtilsRequest).run();
					}
					else if(anfrage.equals(PostLocationServer.cmd_PostComment))
					{
						new PostComment(PostLocationServer.this, this, this.logUtilsRequest).run();
					}
					else if(anfrage.equals(PostLocationServer.cmd_SavePublicPost))
					{
						new SavePublicPost(PostLocationServer.this, this, this.logUtilsRequest).run();
					}
					else if(anfrage.equals(PostLocationServer.cmd_SharePublicPost))
					{
						new SharePublicPost(PostLocationServer.this, this, this.logUtilsRequest).run();
					}
					else if(anfrage.equals(PostLocationServer.cmd_UploadProfilbild))
					{
						new UploadProfilbild(PostLocationServer.this, this, this.logUtilsRequest).run();
					}
					else if(anfrage.equals(PostLocationServer.cmd_DeleteProfilbild))
					{
						new DeleteProfilbild(PostLocationServer.this, this, this.logUtilsRequest).run();
					}
					else if(anfrage.equals(PostLocationServer.cmd_DownloadProfilbild))
					{
						new DownloadProfilbild(PostLocationServer.this, this, this.logUtilsRequest).run();
					}
					else if(anfrage.equals(PostLocationServer.cmd_markChatAsReaded))
					{
						new SendUserOpenedChat(PostLocationServer.this, this, this.logUtilsRequest).run();
					}
				}

				this.writer.close();
				this.reader.close();
				this.socket.close();
				this.logUtilsRequest.writeLog(PostLocationServer.placeholder + "connection: " + this.socket.getInetAddress() + " closed.");
			}
			catch(Exception ec)
			{
				this.logUtilsRequest.writeLog(PostLocationServer.placeholder + "FatalError(): " + ec);
				try
				{
					this.writer.close();
					this.reader.close();
					this.socket.close();
				}
				catch(Exception stupid)
				{
					this.logUtilsRequest.writeLog(PostLocationServer.placeholder + "FatalError in Fatal(): " + stupid);
				}
			}
			finally
			{
				this.connection = pool.returnConnectionToPool(this.connection);
			}
		}


		private boolean isHashtagArrayValid(JSONArray jsonArrayHashtags) throws JSONException
		{
			if(jsonArrayHashtags == null)
				return false;

			for(int counter = 0; counter < jsonArrayHashtags.length(); counter++)
			{
				JSONObject json = jsonArrayHashtags.getJSONObject(counter);
				if(!json.has("TAG")
						|| json.getString("TAG").isEmpty()
						|| json.getString("TAG").length() > EsaphMaxSizes.SIZE_MAX_HASHTAG_LENGTH)
				{
					return false;
				}
			}
			return true;
		}

		private void sendImage(File file) throws IOException
		{
			DataOutputStream dos = null;
			FileInputStream fis = null;
			try
			{
				this.logUtilsRequest.writeLog("SENDING Image: " + file.getAbsolutePath());
				dos = new DataOutputStream(this.socket.getOutputStream());
				fis = new FileInputStream(file);

				long length = file.length();
				this.writer.println(length);
				byte[] buffer = new byte[4096];
				while (fis.read(buffer) > 0)
				{
					dos.write(buffer);
					dos.flush();
				}
				this.logUtilsRequest.writeLog("Post I sent.");
			}
			catch (Exception ec)
			{
				this.logUtilsRequest.writeLog("sendImage() failed: " + ec);
			}
			finally
			{
				if(fis != null)
				{
					fis.close();
				}

				if(dos != null)
				{
					dos.close();
				}
			}
		}

		private void sendVideo(File file) throws IOException
		{
			DataOutputStream dos = null;
			FileInputStream fis = null;
			try
			{
				this.logUtilsRequest.writeLog("SENDING Video: " + file.getAbsolutePath());
				dos = new DataOutputStream(this.socket.getOutputStream());
				fis = new FileInputStream(file);

				long length = file.length();
				this.writer.println(length);
				byte[] buffer = new byte[4096];
				while (fis.read(buffer) > 0)
				{
					dos.write(buffer);
					dos.flush();
				}
				this.logUtilsRequest.writeLog("Post Video sent.");
			}
			catch (Exception ec)
			{
				this.logUtilsRequest.writeLog("sendVideo() failed: " + ec);
			}
			finally
			{
				if(fis != null)
				{
					fis.close();
				}

				if(dos != null)
				{
					dos.close();
				}
			}
		}


		private boolean uploadFoto(File fileCreateNewCity) throws Exception
		{
			this.writer.println("1");
			this.logUtilsRequest.writeLog("Image handling, reading length...");
			long maxLength = Long.parseLong(this.readDataCarefully(10));
			this.logUtilsRequest.writeLog("Image handling, length: " + maxLength);
			this.writer.println("1");
			long readed = 0;

			if(maxLength >= EsaphMaxSizes.SIZE_MAX_IMAGE)
			{
				return false;
			}

			DataInputStream dateiInputStream = null;
			FileOutputStream dateiStream = null;
			this.logUtilsRequest.writeLog("Bild wird hochgeladen.");
			dateiInputStream = new DataInputStream(this.socket.getInputStream());
			dateiStream = new FileOutputStream(fileCreateNewCity);

			int count;
			byte[] buffer = new byte[(int) maxLength]; // or 4096, or more
			while ((count = dateiInputStream.read(buffer)) > 0) //Lie�t image
			{
				dateiStream.write(buffer, 0, count);
				readed = readed+count;
				if(maxLength <= readed)
				{
					break;
				}
			}
			this.logUtilsRequest.writeLog("Finished");
			dateiStream.close();

			if(this.checkImageFile(fileCreateNewCity)) //�berpr�ft datei.
			{
				this.logUtilsRequest.writeLog("PICTURE WRITTEN.");
				return true;
			}
			return false;
		}


		private boolean uploadVideo(File fileVideo) throws Exception
		{
			this.writer.println("1");
			this.logUtilsRequest.writeLog("Video handling, reading length...");
			long maxLength = Long.parseLong(this.readDataCarefully(10));
			this.logUtilsRequest.writeLog("Video handling, length: " + maxLength);
			this.writer.println("1");
			long readed = 0;

			if(maxLength > EsaphMaxSizes.SIZE_MAX_VIDEO) //30 mb max.
			{
				return false;
			}

			DataInputStream dateiInputStream = null;
			FileOutputStream dateiStream = null;
			this.logUtilsRequest.writeLog("Video wird hochgeladen.");
			dateiInputStream = new DataInputStream(this.socket.getInputStream());
			dateiStream = new FileOutputStream(fileVideo);

			int count;
			byte[] buffer = new byte[(int) maxLength]; // or 4096, or more
			while ((count = dateiInputStream.read(buffer)) > 0) //Lie�t image
			{
				this.logUtilsRequest.writeLog("Uploading ... " + readed);
				dateiStream.write(buffer, 0, count);
				readed = readed+count;
				if(maxLength <= readed)
				{
					break;
				}
			}
			this.logUtilsRequest.writeLog("Finished");
			dateiStream.close();

			if(this.isVideoFile(fileVideo)) //�berpr�ft datei. this.isVideoFile(fileVideo)
			{
				this.logUtilsRequest.writeLog("VIDEO WRITTEN.");
				return true;
			}
			return false;
		}


		public String readDataCarefully(int bufferSize) throws Exception
		{
			String msg = this.reader.readLine();
			if(msg == null || msg.length() > bufferSize)
			{
				throw new Exception("Exception: msg " + msg + " length: " + msg.length() + ">" + bufferSize);
			}
			this.logUtilsRequest.writeLog("MSG: " + msg);
			return msg;
		}


		private static final String QUERY_CHECK_SESSION = "SELECT SID FROM Sessions WHERE SID=? AND UID=?";
		public boolean checkSession(long UID, String SID)
		{
			try
			{
				this.logUtilsRequest.writeLog("Checking session");
				PreparedStatement qSID = (PreparedStatement) this.connection.prepareStatement(RequestHandler.QUERY_CHECK_SESSION);
				qSID.setString(1, SID);
				qSID.setLong(2, UID);
				ResultSet result = qSID.executeQuery();
				if(result.next())
				{
					this.logUtilsRequest.writeLog("Session: NEXT()");
					return true;
				}
				else
				{
					this.logUtilsRequest.writeLog("Session: !=NEXT()");
					return false;
				}

			}
			catch(Exception ec)
			{
				this.logUtilsRequest.writeLog("Exception: " + ec);
				return false;
			}
		}

		private boolean checkSID()
		{
			try
			{
				long UID = this.jsonMessage.getLong("USRN");
				String SID = this.jsonMessage.getString("SID");
				if(UID > 0)
				{
					if(checkSession(UID, SID))
					{
						this.logUtilsRequest.writeLog(PostLocationServer.placeholder + "Session OK.");
						this.ThreadUID = UID;
						return true;
					}
					else
					{
						this.logUtilsRequest.writeLog(PostLocationServer.placeholder + "Session WRONG.");
						return false;
					}
				}
				else
				{
					this.logUtilsRequest.writeLog(PostLocationServer.placeholder + " client has passed a null object!");
					return false;
				}
			}
			catch(Exception ec)
			{
				this.logUtilsRequest.writeLog(PostLocationServer.placeholder + "(checkSID): FATAL ERROR");
				return false;
			}
		}

		public long getThreadUID()
		{
			return this.ThreadUID;
		}


		public JSONObject getJSONMessage()
		{
			return this.jsonMessage;
		}


		private boolean checkImageFile(File filepath)
		{
			try
			{
				Image image = ImageIO.read(filepath);
				if (image == null)
				{
					this.logUtilsRequest.writeLog("Image file wrong.");
					return false;
				}
				else
				{
					this.logUtilsRequest.writeLog("Image correct.");
					return true;
				}
			}
			catch(IOException ex)
			{
				this.logUtilsRequest.writeLog("Really bad image.");
				return false;
			}
		}


		private boolean isVideoFile(File file)
		{
			this.logUtilsRequest.writeLog("WARNING: MIMETYPE ISNT CHECKED");
			return true;
		}


		public void getConnectionToSql() throws InterruptedException, SQLException
		{
			this.connection = (Connection) pool.getConnectionFromPool();
		}

		public Connection getCurrentConnectionToSql()
		{
			return this.connection;
		}

		private String generatePID(long UID)
		{
			SecureRandom random = new SecureRandom();
			return new BigInteger(130, random).toString(32) + UID;
		}




		private void DeclineOneFollowShipByIDS(long UID, long FUID) throws SQLException
		{
			//In database only one can follow someonek, when he is following back. He has to follow back.
			PreparedStatement prDeleteByID = null;
			try
			{
				prDeleteByID = (PreparedStatement) this.connection.prepareStatement(PostLocationServer.queryDeclineWatcherAnfrageByIDs);
				prDeleteByID.setLong(1, UID);
				prDeleteByID.setLong(2, FUID);
				prDeleteByID.executeUpdate();
			}
			catch(Exception ec)
			{
				this.logUtilsRequest.writeLog(PostLocationServer.placeholder + "DeclineOneFollowShipByIDS() failed: " + ec);
			}
			finally
			{
				if(prDeleteByID != null)
				{
					prDeleteByID.close();
				}
			}
		}
	}


	private class SendPictureToUser extends Thread //Sending picture akkording to wamp. Ob zum nutzer an sich oder. Nur an momente.
	{
		private LogUtilsEsaph logUtilsRequest;
		private long TimeSent;
		private long UID;
		private JSONArray jsonArrayHashtags;
		private JSONArray jsonArrayWAMP;
		private String PID;
		private long PPID;
		private short FORMAT;
		private String Beschreibung;
		private Connection connection;


		public SendPictureToUser(long UID,
								 JSONArray WAMP,
								 JSONArray jsonArrayHashtags,
								 String PID,
								 long PPID,
								 short FORMAT,
								 String Beschreibung,
								 long TimeSent,
								 LogUtilsEsaph logUtilsRequest)
		{
			this.logUtilsRequest = logUtilsRequest;
			this.jsonArrayHashtags = jsonArrayHashtags;
			this.UID = UID;
			this.Beschreibung = Beschreibung;
			this.TimeSent = TimeSent;
			this.jsonArrayWAMP = WAMP;
			this.PID = PID;
			this.PPID = PPID;
			this.FORMAT = FORMAT;
		}

		public void getConnectionToSql() throws InterruptedException, SQLException
		{
			this.connection = (Connection) pool.getConnectionFromPool();
		}

		@Override
		public void run()
		{
			try
			{
				this.getConnectionToSql();
				JSONArray jsonArrayUsersReceivers = this.jsonArrayWAMP.getJSONArray(0);

				if(jsonArrayUsersReceivers.length() > 0)
				{
					EsaphInternalMessageCreator json = new EsaphInternalMessageCreator(MessageTypeIdentifier.CMD_NewPrivatePost, jsonArrayUsersReceivers);
					json.putInto("USRN", this.UID);
					json.putInto("FT", this.FORMAT);
					json.putInto("DES", this.Beschreibung);
					json.putInto("ARR_EHT", this.jsonArrayHashtags);
					json.putInto("PPID", this.PPID);
					json.putInto("PID", PID);
					json.putInto("TIME_POST", this.TimeSent);
					PostLocationServer.executorSubThreads.submit(new SendInformationToUser(json.getJSON(), this.logUtilsRequest, PostLocationServer.this));
				}
			}
			catch(Exception ec)
			{
				this.logUtilsRequest.writeLog(PostLocationServer.placeholder + "SendPictureToUser failed: " + ec);
			}
			finally
			{
				this.connection = pool.returnConnectionToPool(this.connection);
			}
		}
	}
}
