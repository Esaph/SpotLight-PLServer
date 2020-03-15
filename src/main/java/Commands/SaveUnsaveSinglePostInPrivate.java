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

import java.io.File;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class SaveUnsaveSinglePostInPrivate extends EsaphCommand
{
    private static final String queryGetUserPrivatePostInChatContext = "SELECT * FROM (SELECT * FROM PrivateMoments WHERE PPID=?) AS P JOIN PrivateReceivers ON P.PPID=PrivateReceivers.PPID AND PrivateReceivers.UID_REC=? LIMIT 1";
    private static final String queryIsPostSavedByUser = "SELECT * FROM PrivateMomentsSaved WHERE UID_POST_FROM=? AND UID_SAVED=? AND PPID=?";
    private static final String queryInsertPostSaved = "INSERT INTO PrivateMomentsSaved (UID_POST_FROM, UID_SAVED, PPID) values (?, ?, ?)";
    private static final String queryRemoveFromSavedPosts = "DELETE FROM PrivateMomentsSaved WHERE UID_SAVED=? AND PPID=?";
    private static final String queryLookUpIfPostSavedWith24HoursCheck = "SELECT * FROM PrivateMoments WHERE PPID=? AND TIME <= DATE_SUB(NOW(), INTERVAL 24 HOUR) AND NOT EXISTS(SELECT NULL FROM PrivateMomentsSaved WHERE PrivateMoments.PPID = PrivateMomentsSaved.PPID)";
    private static final String queryDeletePrivatePostComplete = "DELETE FROM PrivateMoments WHERE UID=? AND PPID=?";
    private static final String queryDeleteAllHashtagsFromPost = "DELETE FROM TAGS WHERE PPID=?";


    public SaveUnsaveSinglePostInPrivate(PostLocationServer plServer, PostLocationServer.RequestHandler requestHandler, LogUtilsEsaph logUtilsRequest) {
        super(plServer, requestHandler, logUtilsRequest);
    }

    @Override
    public void run() throws Exception
    {
        int saveStatus = -1;
        long abs = -1;

        long REC_ID = super.requestHandler().getJSONMessage().getLong("REC_ID"); //Key chat partner.

        PreparedStatement preparedGetSingleMomentPost = (PreparedStatement) super.requestHandler().getCurrentConnectionToSql().prepareStatement(SaveUnsaveSinglePostInPrivate.queryGetUserPrivatePostInChatContext);
        preparedGetSingleMomentPost.setLong(1, super.requestHandler().getJSONMessage().getLong("PPID"));
        preparedGetSingleMomentPost.setLong(2, REC_ID);

        System.out.println("SUTEST: Called");

        ResultSet result = preparedGetSingleMomentPost.executeQuery();

        if(result.next())
        {
            System.out.println("SUTEST: Found post");
            abs = result.getLong("UID");

            PreparedStatement prCheckIfSaved = (PreparedStatement) super.requestHandler().getCurrentConnectionToSql().prepareStatement(SaveUnsaveSinglePostInPrivate.queryIsPostSavedByUser);
            prCheckIfSaved.setLong(1, abs);
            prCheckIfSaved.setLong(2, super.requestHandler().getThreadUID());
            prCheckIfSaved.setLong(3, result.getLong("PPID"));
            ResultSet resultCheckFirst = prCheckIfSaved.executeQuery();
            if(!resultCheckFirst.next()) //SAVING
            {
                PreparedStatement prInsertToSavedPosts = (PreparedStatement) super.requestHandler().getCurrentConnectionToSql().prepareStatement(SaveUnsaveSinglePostInPrivate.queryInsertPostSaved);
                prInsertToSavedPosts.setLong(1, abs);
                prInsertToSavedPosts.setLong(2, super.requestHandler().getThreadUID());
                prInsertToSavedPosts.setLong(3, result.getLong("PPID"));
                prInsertToSavedPosts.executeUpdate();
                prInsertToSavedPosts.close();
                saveStatus = 1;
            }
            else //CHECK WHO SAVED AND WHO IS THREAD UID
            {
                PreparedStatement prRemoveFromSaved = (PreparedStatement) super.requestHandler().getCurrentConnectionToSql().prepareStatement(SaveUnsaveSinglePostInPrivate.queryRemoveFromSavedPosts);
                prRemoveFromSaved.setLong(1, super.requestHandler().getThreadUID());
                prRemoveFromSaved.setLong(2, result.getLong("PPID"));
                prRemoveFromSaved.executeUpdate();
                prRemoveFromSaved.close();
                saveStatus = 2;


                PreparedStatement preparedStatementCheckIfNoOneSaved = (PreparedStatement) super.requestHandler().getCurrentConnectionToSql().prepareStatement(SaveUnsaveSinglePostInPrivate.queryLookUpIfPostSavedWith24HoursCheck);
                preparedStatementCheckIfNoOneSaved.setLong(1, super.requestHandler().getJSONMessage().getLong("PPID"));
                ResultSet resultSetNotSaved = preparedStatementCheckIfNoOneSaved.executeQuery();
                if(resultSetNotSaved.next())
                {
                    EsaphStoringHandler esaphStoringHandler = new EsaphStoringHandler();
                    File fileHQ = esaphStoringHandler.getStoringFile(result.getString("PID"), EsaphStoragePaths.PATH_PRIVATE_UPLOADS);

                    if(fileHQ != null)
                    {
                        fileHQ.delete();
                    }

                    PreparedStatement preparedStatementRemoveHashtags = //Removing all Hashtags.
                            (PreparedStatement) super.requestHandler().getCurrentConnectionToSql()
                            .prepareStatement(SaveUnsaveSinglePostInPrivate.queryDeleteAllHashtagsFromPost);
                    preparedStatementRemoveHashtags.executeUpdate();
                    preparedStatementRemoveHashtags.close();

                    PreparedStatement removePostInPrivateComplete = (PreparedStatement) super.requestHandler().getCurrentConnectionToSql().prepareStatement(SaveUnsaveSinglePostInPrivate.queryDeletePrivatePostComplete);
                    removePostInPrivateComplete.setLong(1, super.requestHandler().getThreadUID());
                    removePostInPrivateComplete.setLong(2, super.requestHandler().getJSONMessage().getLong("PPID"));
                    removePostInPrivateComplete.executeUpdate();
                    removePostInPrivateComplete.close();
                }
                resultSetNotSaved.close();
                preparedStatementCheckIfNoOneSaved.close();
            }
            prCheckIfSaved.close();
            resultCheckFirst.close();
        }

        preparedGetSingleMomentPost.close();
        result.close();

        if(saveStatus > -1)
        {
            super.requestHandler().getWriter().println(saveStatus);
            if(saveStatus == 1)
            {
                if(super.requestHandler().getThreadUID() != abs)
                {
                    EsaphInternalMessageCreator jsonMessage = new EsaphInternalMessageCreator(MessageTypeIdentifier.CMD_UserSavedYourPostPrivate, abs);
                    jsonMessage.putInto("USRN", super.requestHandler().getThreadUID());
                    jsonMessage.putInto("PPID", super.requestHandler().getJSONMessage().getLong("PPID"));
                    super.plServer().getExecutorSubThreads().submit(new SendInformationToUser(jsonMessage.getJSON(), super.logUtilsRequest(), super.plServer()));
                }

            }
            else if(saveStatus == 2)
            {
                if(super.requestHandler().getThreadUID() != abs)
                {
                    EsaphInternalMessageCreator jsonMessage = new EsaphInternalMessageCreator(MessageTypeIdentifier.CMD_UserUnsavedYourPostPrivate, abs);
                    jsonMessage.putInto("USRN", super.requestHandler().getThreadUID());
                    jsonMessage.putInto("PPID", super.requestHandler().getJSONMessage().getLong("PPID"));
                    super.plServer().getExecutorSubThreads().submit(new SendInformationToUser(jsonMessage.getJSON(), super.logUtilsRequest(), super.plServer()));
                }
            }
        }
        else
        {
            super.requestHandler().getWriter().println("-1");
        }
    }
}
