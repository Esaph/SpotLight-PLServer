/*
 *  Copyright (C) Esaph, Julian Auguscik - All Rights Reserved
 *  * Unauthorized copying of this file, via any medium is strictly prohibited
 *  * Proprietary and confidential
 *  * Written by Julian Auguscik <esaph.re@gmail.com>, March  2020
 *
 */

package Commands;

import java.awt.Image;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.sql.ResultSet;
import java.util.UUID;
import javax.imageio.ImageIO;

import Esaph.*;
import org.apache.commons.io.FileUtils;
import org.json.JSONObject;

import com.mysql.jdbc.PreparedStatement;
import PLServerMain.PostLocationServer;
import PLServerMain.PostLocationServer.RequestHandler;

public class SendSticker extends EsaphCommand
{
	private static final String QUERY_CHECK_IF_STICKER_EXISTS_IN_PACK = "SELECT 1 FROM Stickers WHERE LSID=? AND LSPID=? LIMIT 1";
	private static final String QUERY_GET_STICKERPACK = "SELECT LSPID FROM StickerPack WHERE LSPID=? LIMIT 1";

	private static final String QUERY_INSERT_NEW_STICKER = "INSERT INTO Stickers (UID_CREATOR, LSPID, LSID, STICKER_PATH) values (?, ?, ?, ?)";
	private static final String QUERY_INSERT_NEW_STICKER_PACK = "INSERT INTO StickerPack (UID_CREATOR, PACK_NAME, LSPID) values (?, ?, ?)";

	public SendSticker(PostLocationServer plServer, RequestHandler requestHandler, LogUtilsEsaph logUtilsRequest)
	{
		super(plServer, requestHandler, logUtilsRequest);
	}

	@Override
	public void run() throws Exception
	{
		PreparedStatement prCheckIfStickerAvaiable = null;
		ResultSet resultCheckIfStickerAvaiable = null;
		PreparedStatement getStickerPackID = null;
		ResultSet resultgetStickerPackID = null;

		try
		{
			long timeSent = System.currentTimeMillis();
			JSONObject jsonObjectStickerAdded = new JSONObject();
			long FUID = super.requestHandler().getJSONMessage().getLong("FUSRN");

			long LSID = super.requestHandler().getJSONMessage().getLong("LSID");
			long LSPID = super.requestHandler().getJSONMessage().getLong("LSPID");
			String ST_IDPID = super.requestHandler().getJSONMessage().getString("STID");

			long msgHash = UUID.nameUUIDFromBytes(("" + LSID + timeSent).getBytes()).getMostSignificantBits();

			prCheckIfStickerAvaiable = (PreparedStatement)
					super.requestHandler().getCurrentConnectionToSql().prepareStatement(SendSticker.QUERY_CHECK_IF_STICKER_EXISTS_IN_PACK);

			prCheckIfStickerAvaiable.setLong(1, LSID);
			prCheckIfStickerAvaiable.setLong(2, LSPID);

			resultCheckIfStickerAvaiable = prCheckIfStickerAvaiable.executeQuery();


			if(!resultCheckIfStickerAvaiable.next()) //Sticker not exists
			{
				EsaphStoringHandler esaphStoringHandler = new EsaphStoringHandler();
				ST_IDPID = esaphStoringHandler.generatePID();

				File fileSticker = esaphStoringHandler.getStoringFile(ST_IDPID, EsaphStoragePaths.STICKER_FILES_PATH);
				File fileTemp = esaphStoringHandler.getTempFile(EsaphDataPrefix.LF_STICKER_PREFIX, super.requestHandler().getThreadUID());


				if(ServerPolicy.isAllowed(super.requestHandler().getCurrentConnectionToSql(),
						super.requestHandler().getThreadUID(),
						FUID))
				{
					super.requestHandler().getWriter().println("0");

					super.requestHandler().returnConnectionToPool();
					if(this.uploadSticker(fileTemp, fileSticker))
					{
						super.requestHandler().getConnectionToSql();


						getStickerPackID =
								(PreparedStatement) super.requestHandler().getCurrentConnectionToSql().prepareStatement(SendSticker.QUERY_GET_STICKERPACK);
						getStickerPackID.setLong(1, LSPID);
						resultgetStickerPackID = getStickerPackID.executeQuery();

						if(resultgetStickerPackID.next()) //Add sticker to pack.
						{
							PreparedStatement prInsertNewSticker =
									(PreparedStatement) super.requestHandler().getCurrentConnectionToSql().prepareStatement(SendSticker.QUERY_INSERT_NEW_STICKER);
							prInsertNewSticker.setLong(1, super.requestHandler().getThreadUID());
							prInsertNewSticker.setLong(2, resultgetStickerPackID.getLong("LSPID"));
							prInsertNewSticker.setLong(3, LSID);
							prInsertNewSticker.setString(4, ST_IDPID);
							prInsertNewSticker.executeUpdate();
							prInsertNewSticker.close();

							jsonObjectStickerAdded.put("LSPID", resultgetStickerPackID.getLong("LPPID")); //New StickerPackSid
							jsonObjectStickerAdded.put("LSID", LSID); //New StickerSid
							jsonObjectStickerAdded.put("STID", ST_IDPID);
						}
						else //Create new stickerpack
						{
							String PACK_NAME_FROM_CLIENT = super.requestHandler().getJSONMessage().getString("PN"); //Pack name.

							if(checkPackName(PACK_NAME_FROM_CLIENT))
							{
								PreparedStatement createNewStickerPack = //Created a new Pack.
										(PreparedStatement) super.requestHandler().getCurrentConnectionToSql().prepareStatement(SendSticker.QUERY_INSERT_NEW_STICKER_PACK);
								createNewStickerPack.setLong(1, super.requestHandler().getThreadUID());
								createNewStickerPack.setString(2, PACK_NAME_FROM_CLIENT);
								createNewStickerPack.setLong(3, LSPID);
								createNewStickerPack.executeUpdate();
								createNewStickerPack.close();

								PreparedStatement prInsertNewSticker =
										(PreparedStatement) super.requestHandler().getCurrentConnectionToSql().prepareStatement(SendSticker.QUERY_INSERT_NEW_STICKER);
								prInsertNewSticker.setLong(1, super.requestHandler().getThreadUID());
								prInsertNewSticker.setLong(2, LSPID);
								prInsertNewSticker.setLong(3, LSID);
								prInsertNewSticker.setString(4, ST_IDPID);
								prInsertNewSticker.executeUpdate();
								prInsertNewSticker.close();

								jsonObjectStickerAdded.put("LSID", LSID); //New StickerSid
								jsonObjectStickerAdded.put("LSPID", LSPID); //New StickerPackSid
								jsonObjectStickerAdded.put("STID", ST_IDPID);
							}
						}
					}

					EsaphInternalMessageCreator json = new EsaphInternalMessageCreator(MessageTypeIdentifier.CMD_NEW_STICKER, FUID);
					json.putInto("USRN", super.requestHandler().getThreadUID());
					json.putInto("PAYLOAD", jsonObjectStickerAdded);
					json.putInto("MH", msgHash);
					json.putInto("PLINF", super.requestHandler().getJSONMessage().getJSONObject("PLINF"));
					json.putInto("TIME", timeSent);

					jsonObjectStickerAdded.put("MH", msgHash);
					super.requestHandler().getWriter().println(jsonObjectStickerAdded.toString()); //DONE

					super.plServer().getExecutorSubThreads().submit(new SendInformationToUser(json.getJSON(),
							super.logUtilsRequest(),
							super.plServer()));
				}
			}
			else
			{
				if(ServerPolicy.isAllowed(super.requestHandler().getCurrentConnectionToSql(),
						super.requestHandler().getThreadUID(),
						FUID))
				{
					jsonObjectStickerAdded.put("LSPID", LSPID);
					jsonObjectStickerAdded.put("LSID", LSID);
					jsonObjectStickerAdded.put("STID", ST_IDPID);

					EsaphInternalMessageCreator json = new EsaphInternalMessageCreator(MessageTypeIdentifier.CMD_NEW_STICKER, FUID);
					json.putInto("USRN", super.requestHandler().getThreadUID());
					json.putInto("PAYLOAD", jsonObjectStickerAdded);
					json.putInto("MH", msgHash);
					json.putInto("PLINF", super.requestHandler().getJSONMessage().getJSONObject("PLINF"));
					json.putInto("TIME", timeSent);

					super.requestHandler().getWriter().println("1"); //DONE

					super.plServer().getExecutorSubThreads().submit(new SendInformationToUser(json.getJSON(),
							super.logUtilsRequest(),
							super.plServer()));
				}
			}
		}
		catch (Exception ec)
		{
			super.logUtilsRequest().writeLog("SendSticker failed: " + ec);
		}
		finally
		{
			if(prCheckIfStickerAvaiable != null)
			{
				prCheckIfStickerAvaiable.close();
			}

			if(resultCheckIfStickerAvaiable != null)
			{
				resultCheckIfStickerAvaiable.close();
			}

			if(getStickerPackID != null)
			{
				getStickerPackID.close();
			}

			if(resultgetStickerPackID != null)
			{
				resultgetStickerPackID.close();
			}
		}
	}


	private boolean uploadSticker(File TEMP, File FILE_STICKER) throws Exception
	{
		super.requestHandler().getWriter().println("1");
		super.logUtilsRequest().writeLog("Sticker handling, reading length...");
		long maxLength = Long.parseLong(super.requestHandler().readDataCarefully(10));
		super.logUtilsRequest().writeLog("Sticker handling, length: " + maxLength);
		super.requestHandler().getWriter().println("1");
		long readed = 0;
		
		if(maxLength > EsaphMaxSizes.SIZE_MAX_STICKER)
		{
			super.logUtilsRequest().writeLog("uploadSticker(), sticker file is too big.");
			return false;
		}
		
		DataInputStream dateiInputStream = null;
		FileOutputStream dateiStream = null;
		super.logUtilsRequest().writeLog("Sticker wird hochgeladen.");
		dateiInputStream = new DataInputStream(super.requestHandler().getSocket().getInputStream());
		dateiStream = new FileOutputStream(TEMP);
		
		int count;
		byte[] buffer = new byte[(int) maxLength]; // or 4096, or more
		while ((count = dateiInputStream.read(buffer)) > 0) //Lieﬂt image
		{
			dateiStream.write(buffer, 0, count);
			readed = readed+count;
			if(maxLength <= readed)
			{
				break;
			}
		}	
		super.logUtilsRequest().writeLog("Finished");
		dateiStream.close();
		
		if(this.isStickerFile(TEMP))
		{
			super.logUtilsRequest().writeLog("STICKER WRITTEN.");
			FileUtils.copyFile(TEMP, FILE_STICKER);
			if(TEMP != null) //nur ein mal lˆschen. :) =) :D
			{
				TEMP.delete();
			}
			return true;
		}
		else
		{
			if(TEMP != null) //nur ein mal lˆschen. :) =) :D
			{
				TEMP.delete();
			}
			
			if(FILE_STICKER != null) //nur ein mal lˆschen. :) =) :D
			{
				FILE_STICKER.delete();
			}
		}
		return false;
	}
	
	private boolean isStickerFile(File FILE_STICKER)
	{
		try
		{
		    Image image = ImageIO.read(FILE_STICKER);
		    if (image == null)
		    {
		        super.logUtilsRequest().writeLog("Sticker file wrong.");
		        return false;
		    }
		    else
		    {
		    	super.logUtilsRequest().writeLog("Sticker correct.");
		    	return true;
		    }
		}
		catch(IOException ex)
		{
			super.logUtilsRequest().writeLog("Really bad Sticker.");
		    return false;
		}
	}
	
	private boolean checkPackName(String PACKNAME)
	{
		if(PACKNAME == null || PACKNAME.isEmpty() || PACKNAME.length() > EsaphMaxSizes.SIZE_MAX_STICKER_PACKNAME_LENGTH)
			return false;
		
		
		return true;
	}
	
}
