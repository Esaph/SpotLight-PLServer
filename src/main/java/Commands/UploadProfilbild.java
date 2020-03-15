/*
 *  Copyright (C) Esaph, Julian Auguscik - All Rights Reserved
 *  * Unauthorized copying of this file, via any medium is strictly prohibited
 *  * Proprietary and confidential
 *  * Written by Julian Auguscik <esaph.re@gmail.com>, March  2020
 *
 */

package Commands;

import Esaph.*;
import PLServerMain.PostLocationServer;
import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.imageio.ImageIO;
import java.awt.*;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class UploadProfilbild extends EsaphCommand
{
    public UploadProfilbild(PostLocationServer plServer, PostLocationServer.RequestHandler requestHandler, LogUtilsEsaph logUtilsRequest)
    {
        super(plServer, requestHandler, logUtilsRequest);
    }

    @Override
    public void run() throws Exception
    {
        EsaphStoringHandler esaphStoringHandler = new EsaphStoringHandler();
        File fileTemp = esaphStoringHandler.getTempFile(EsaphDataPrefix.JPG_PREFIX, super.requestHandler().getThreadUID());
        File fileProfilbildStoringPath = esaphStoringHandler.getStoringFile(Long.toString(super.requestHandler().getThreadUID()), EsaphStoragePaths.PATH_PROFILBILDER);

        try
        {

            if(uploadProfilbild(fileTemp))
            {
                FileUtils.copyFile(fileTemp, fileProfilbildStoringPath);

                super.requestHandler().getWriter().println("1");

                EsaphInternalMessageCreator esaphInternalMessageCreator = new EsaphInternalMessageCreator(MessageTypeIdentifier.CMD_PB_UPDATE,
                        getMyFriends());
                esaphInternalMessageCreator.putInto("USNR", super.requestHandler().getThreadUID());
                esaphInternalMessageCreator.putInto("TIME", System.currentTimeMillis());

                super.plServer().getExecutorSubThreads().submit(new SendInformationToUser(esaphInternalMessageCreator.getJSON(),
                        super.logUtilsRequest(),
                        super.plServer()));
            }
            else
            {
                super.requestHandler().getWriter().println("0");
            }
        }
        catch (Exception ec)
        {
            super.logUtilsRequest().writeLog("UploadProfilbild() failed: " + ec);
            if(fileProfilbildStoringPath != null)
            {
                fileProfilbildStoringPath.delete();
            }
        }
        finally
        {
            if(fileTemp != null) //nur ein mal lˆschen. :) =) :D
            {
                fileTemp.delete();
            }
        }
    }


    private static final String queryUserFriends = "SELECT * FROM Watcher WHERE UID=? AND AD=0 OR FUID=? AND AD=0";
    private JSONArray getMyFriends() throws SQLException
    {
        JSONArray friendUid = new JSONArray();
        PreparedStatement preparedStatementGetFriends = null;
        ResultSet resultGetFriends = null;
        try
        {
            preparedStatementGetFriends = (PreparedStatement) super.requestHandler().getCurrentConnectionToSql()
                    .prepareStatement(UploadProfilbild.queryUserFriends);
            preparedStatementGetFriends.setLong(1, super.requestHandler().getThreadUID());
            preparedStatementGetFriends.setLong(2, super.requestHandler().getThreadUID());
            resultGetFriends = preparedStatementGetFriends.executeQuery();

            while (resultGetFriends.next())
            {
                JSONObject jsonObject = new JSONObject();
                if (resultGetFriends.getLong("UID") == super.requestHandler().getThreadUID())
                {
                    jsonObject.put("REC_ID", resultGetFriends.getLong("FUID"));
                }
                else {
                    jsonObject.put("REC_ID", resultGetFriends.getLong("UID"));
                }
                friendUid.put(jsonObject);
            }
            return friendUid;
        }
        catch (Exception ec)
        {
            super.logUtilsRequest().writeLog("getMyFriends() failed: " + ec);
            return new JSONArray();
        }
        finally
        {
            if(preparedStatementGetFriends != null)
            {
                preparedStatementGetFriends.close();
            }

            if(resultGetFriends != null)
            {
                resultGetFriends.close();
            }
        }
    }

    private String generatePID(long UID)
    {
        SecureRandom random = new SecureRandom();
        return new BigInteger(130, random).toString(32) + UID;
    }


    private boolean uploadProfilbild(File fileCreateNewCity) throws Exception
    {
        super.logUtilsRequest().writeLog("Image handling, reading length...");
        long maxLength = Long.parseLong(super.requestHandler().readDataCarefully(10));
        super.logUtilsRequest().writeLog("Image handling, length: " + maxLength);
        super.requestHandler().getWriter().println("1");
        long readed = 0;

        if(maxLength >= EsaphMaxSizes.SIZE_MAX_PROFILBILD)
        {
            return false;
        }

        DataInputStream dateiInputStream = null;
        FileOutputStream dateiStream = null;
        super.logUtilsRequest().writeLog("Bild wird hochgeladen.");
        dateiInputStream = new DataInputStream(super.requestHandler().getSocket().getInputStream());
        dateiStream = new FileOutputStream(fileCreateNewCity);

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

        if(this.checkImageFile(fileCreateNewCity)) //‹berpr¸ft datei.
        {
            super.logUtilsRequest().writeLog("PICTURE WRITTEN.");
            return true;
        }
        return false;
    }


    private boolean checkImageFile(File filepath)
    {
        try
        {
            Image image = ImageIO.read(filepath);
            if (image == null)
            {
                super.logUtilsRequest().writeLog("Image file wrong.");
                return false;
            }
            else
            {
                super.logUtilsRequest().writeLog("Image correct.");
                return true;
            }
        }
        catch(IOException ex)
        {
            super.logUtilsRequest().writeLog("Really bad image.");
            return false;
        }
    }


}
