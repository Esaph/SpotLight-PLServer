/*
 *  Copyright (C) Esaph, Julian Auguscik - All Rights Reserved
 *  * Unauthorized copying of this file, via any medium is strictly prohibited
 *  * Proprietary and confidential
 *  * Written by Julian Auguscik <esaph.re@gmail.com>, March  2020
 *
 */

package Commands;

import Esaph.EsaphInternalMessageCreator;
import Esaph.LogUtilsEsaph;
import Esaph.MessageTypeIdentifier;
import Esaph.SendInformationToUser;
import PLServerMain.PostLocationServer;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

public class SharePost extends EsaphCommand
{
    //Warning: Sharing not checking if you have the ability to share a post. So if some has the pid, he can access the post by sharing it.
    //Example, creating 2 accounts, making them friends. Than the first account share a picture with the stolen pid to the second account, and vuala someone can access the image without receiving it.

    //Update: Fixed the problem on the same day, with sharing. But not in downloading the image.

    private static final String QUERY_LOOK_UP_POST = "SELECT UID, FUID, PID , TYPE FROM PrivateMoments WHERE PID=? LIMIT 1";
    private static final String QUERY_CHECK_IF_SHARED_TO_ME = "SELECT 1 FROM Shared WHERE FUID=? AND PID=? LIMIT 1";
    private static final String QUERY_CHECK_IF_MY_POST = "SELECT 1 FROM PrivateMoments WHERE UID=? AND PID=? LIMIT 1";
    private static final String QUERY_INSERT_NEW_SHARED = "INSERT INTO FROM Shared (UID=?, FUID=?, PID=?) values (?, ?, ?)";

    public SharePost(PostLocationServer plServer, PostLocationServer.RequestHandler requestHandler, LogUtilsEsaph logUtilsRequest)
    {
        super(plServer, requestHandler, logUtilsRequest);
    }


    @Override
    public void run() throws Exception
    {
        long timeSent = System.currentTimeMillis();

        String POST_PID = super.requestHandler().getJSONMessage().getString("PID");
        long UsernameShareWithId = super.requestHandler().getJSONMessage().getLong("FUSRN");
        long msgHash = UUID.nameUUIDFromBytes((POST_PID + timeSent).getBytes()).getMostSignificantBits();

        if(POST_PID == null || POST_PID.isEmpty() || UsernameShareWithId < 0)
            return;

        if(canSharePost(POST_PID))
        {
            PreparedStatement preparedStatementLookUpPost = super.requestHandler().getCurrentConnectionToSql().prepareStatement(SharePost.QUERY_LOOK_UP_POST);
            preparedStatementLookUpPost.setString(1, POST_PID);
            ResultSet resultSetLookUpPost = preparedStatementLookUpPost.executeQuery();
            if(resultSetLookUpPost.next())
            {
                long UID_POST_FROM = (resultSetLookUpPost.getLong("UID"));

                PreparedStatement preparedStatementInsertNewShared = (PreparedStatement) super.requestHandler().getCurrentConnectionToSql().prepareStatement(SharePost.QUERY_INSERT_NEW_SHARED);
                preparedStatementInsertNewShared.setLong(1, super.requestHandler().getThreadUID());
                preparedStatementInsertNewShared.setLong(2, UID_POST_FROM);
                preparedStatementInsertNewShared.setString(3, POST_PID);
                int result = preparedStatementInsertNewShared.executeUpdate();
                preparedStatementInsertNewShared.close();

                if(result > 0)
                    {
                    super.requestHandler().getWriter().println("1");

                    EsaphInternalMessageCreator esaphInternalMessageCreator = new EsaphInternalMessageCreator(MessageTypeIdentifier.CMD_NEW_SHARED_POST,
                            UsernameShareWithId);

                    esaphInternalMessageCreator.putInto("USRN", super.requestHandler().getThreadUID());
                    esaphInternalMessageCreator.putInto("MH", msgHash);
                    esaphInternalMessageCreator.putInto("TP", resultSetLookUpPost.getLong("TYPE"));
                    esaphInternalMessageCreator.putInto("PID", resultSetLookUpPost.getLong("PID"));
                    esaphInternalMessageCreator.putInto("OU", UID_POST_FROM);
                    esaphInternalMessageCreator.putInto("PID", POST_PID);
                    esaphInternalMessageCreator.putInto("TIME", timeSent);

                    super.plServer().getExecutorSubThreads().submit(new SendInformationToUser(esaphInternalMessageCreator.getJSON(), super.logUtilsRequest(), super.plServer()));
                }
            }

            resultSetLookUpPost.close();
            preparedStatementLookUpPost.close();
        }
    }



    private boolean canSharePost(String POST_PID)
    {
        try
        {
            PreparedStatement preparedStatement = super.requestHandler().getCurrentConnectionToSql().prepareStatement(SharePost.QUERY_CHECK_IF_SHARED_TO_ME);
            preparedStatement.setLong(1, super.requestHandler().getThreadUID());
            preparedStatement.setString(2, POST_PID);
            ResultSet resultSet = preparedStatement.executeQuery();
            if(resultSet.next())
            {
                preparedStatement.close();
                resultSet.close();
                return true;
            }
            preparedStatement.close();
            resultSet.close();

            PreparedStatement preparedStatementIstMyPost = super.requestHandler().getCurrentConnectionToSql().prepareStatement(SharePost.QUERY_CHECK_IF_MY_POST);
            preparedStatementIstMyPost.setLong(1, super.requestHandler().getThreadUID());
            preparedStatementIstMyPost.setString(2, POST_PID);
            ResultSet resultSetMyPost = preparedStatementIstMyPost.executeQuery();
            if(resultSetMyPost.next())
            {
                preparedStatementIstMyPost.close();
                resultSetMyPost.close();
                return true;
            }
            preparedStatementIstMyPost.close();
            resultSetMyPost.close();
        }
        catch (Exception ec)
        {
            super.logUtilsRequest().writeLog("canSharePost() failed: " + ec);
        }

        return false;
    }
}
