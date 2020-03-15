/*
 *  Copyright (C) Esaph, Julian Auguscik - All Rights Reserved
 *  * Unauthorized copying of this file, via any medium is strictly prohibited
 *  * Proprietary and confidential
 *  * Written by Julian Auguscik <esaph.re@gmail.com>, March  2020
 *
 */

package Commands;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.math.BigInteger;
import java.security.SecureRandom;

import Esaph.*;
import org.apache.commons.io.FileUtils;
import com.mysql.jdbc.PreparedStatement;
import PLServerMain.PostLocationServer;
import PLServerMain.PostLocationServer.RequestHandler;

public class SendAudio extends EsaphCommand
{
	private static final String QUERY_INSERT_NEW_AUDIO = "INSERT INTO Memo (UID_ABSENDER, UID_EMPFANGER, AID, AUDIO_PATH) values (?, ? ,?, ?)";
	
	public SendAudio(PostLocationServer plServer, RequestHandler requestHandler, LogUtilsEsaph logUtilsRequest)
	{
		super(plServer, requestHandler, logUtilsRequest);
	}

	@Override
	public void run() throws Exception
	{
		PreparedStatement prInsertNewAudio = null;
		try
		{
			EsaphStoringHandler esaphStoringHandler = new EsaphStoringHandler();
			String AID = esaphStoringHandler.generatePID();
			long FUID = super.requestHandler().getJSONMessage().getLong("FUSRN");
			File fileAudio = esaphStoringHandler.getStoringFile(EsaphDataPrefix.AUDIO_PREFIX, EsaphStoragePaths.AUDIO_FILES_PATH);

			File fileTemp = esaphStoringHandler.getTempFile(EsaphDataPrefix.AUDIO_PREFIX, super.requestHandler().getThreadUID());
			if(ServerPolicy.isAllowed(super.requestHandler().getCurrentConnectionToSql(),
					super.requestHandler().getThreadUID(),
					FUID))
			{
				super.requestHandler().returnConnectionToPool();
				if(this.uploadAudio(fileTemp, fileAudio))
				{
					super.requestHandler().getConnectionToSql();
					prInsertNewAudio =
							(PreparedStatement) super.requestHandler().getCurrentConnectionToSql().prepareStatement(SendAudio.QUERY_INSERT_NEW_AUDIO);
					prInsertNewAudio.setLong(1, super.requestHandler().getThreadUID());
					prInsertNewAudio.setLong(2, FUID);
					prInsertNewAudio.setString(3, AID);
					prInsertNewAudio.setString(4, fileAudio.getAbsolutePath());
					int result = prInsertNewAudio.executeUpdate();
					prInsertNewAudio.close();

					if(result > 0)
					{
						EsaphInternalMessageCreator json = new EsaphInternalMessageCreator(MessageTypeIdentifier.CMD_NEW_AUDIO, FUID);
						json.putInto("USRN", super.requestHandler().getThreadUID());
						json.putInto("AID", AID);
						json.putInto("PLINF", super.requestHandler().getJSONMessage().getJSONObject("PLINF"));
						super.requestHandler().getWriter().println(AID); //DONE

						super.plServer().getExecutorSubThreads().submit(new SendInformationToUser(json.getJSON(),
								super.logUtilsRequest(),
								super.plServer()));
					}
				}
			}
		}
		catch (Exception ec)
		{

		}
		finally {
			if(prInsertNewAudio != null)
			{
				prInsertNewAudio.close();
			}
		}
	}

	private boolean uploadAudio(File TEMP, File FILE_AUDIO) throws Exception
	{
		try
		{
			super.requestHandler().getWriter().println("1");
			super.logUtilsRequest().writeLog("Audio handling, reading length...");
			long maxLength = Long.parseLong(super.requestHandler().readDataCarefully(10));
			super.logUtilsRequest().writeLog("Audio handling, length: " + maxLength);
			super.requestHandler().getWriter().println("1");
			long readed = 0;

			if(maxLength > EsaphMaxSizes.SIZE_MAX_AUDIO)
			{
				return false;
			}

			DataInputStream dateiInputStream = null;
			FileOutputStream dateiStream = null;
			super.logUtilsRequest().writeLog("Audio wird hochgeladen.");
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

			if(this.isAudioFile(TEMP)) //‹berpr¸ft datei. this.isAudioFile(FILE_AUDIO)
			{
				super.logUtilsRequest().writeLog("AUDIO WRITTEN.");
				FileUtils.copyFile(TEMP, FILE_AUDIO);
				return true;
			}
			else
			{
				if(FILE_AUDIO != null) //nur ein mal lˆschen. :) =) :D
				{
					FILE_AUDIO.delete();
				}
			}
		}
		catch (Exception ec)
		{
			//Nothing to print, just save the deleting of temp files with the try catch problem.
		}
		finally
		{
			if(TEMP != null) //nur ein mal lˆschen. :) =) :D
			{
				TEMP.delete();
			}
		}

		return false;
	}
	
	
	private boolean isAudioFile(File FILE_AUDIO)
	{
		return true;
	}
	
}
