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

import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class SavePublicPost extends EsaphCommand
{
    private static final String QUERY_IS_SAVED_BYME_PUBLIC = "SELECT NULL FROM PublicPostsSaved WHERE UID_SAVED=? AND PID=? LIMIT 1";
    private static final String QUERY_INSERT_NEW_SAVED = "INSERT INTO PublicPostsSaved (UID_POST_FROM, UID_SAVED, PID) values (?, ?, ?)";
    private static final String QUERYS_GET_PUBLIC_POST_BY_PID = "SELECT * FROM PublicPosts WHERE PID=? LIMIT 1";
    private static final String QUERY_REMOVE_SAVED = "DELETE FROM PublicPostsSaved WHERE UID_SAVED=? AND PID=? LIMIT 1";

    public SavePublicPost(PostLocationServer plServer, PostLocationServer.RequestHandler requestHandler, LogUtilsEsaph logUtilsRequest) {
        super(plServer, requestHandler, logUtilsRequest);
    }

    @Override
    public void run() throws Exception
    {
        PreparedStatement preparedStatementPost = null;
        ResultSet resultSetPost = null;
        PreparedStatement preparedStatementCheckSaved = null;
        ResultSet resultSetCheckSaved = null;

        try
        {
            preparedStatementPost = (PreparedStatement)
            super.requestHandler().getCurrentConnectionToSql()
            .prepareStatement(SavePublicPost.QUERYS_GET_PUBLIC_POST_BY_PID);
            preparedStatementPost.setString(1, super.requestHandler().getJSONMessage().getString("PID"));
            resultSetPost = preparedStatementPost.executeQuery();

            if(resultSetPost.next()) //Post exists.
            {
                preparedStatementCheckSaved = (PreparedStatement) super.requestHandler().getCurrentConnectionToSql()
                        .prepareStatement(SavePublicPost.QUERY_IS_SAVED_BYME_PUBLIC);
                preparedStatementCheckSaved.setLong(1, super.requestHandler().getThreadUID());
                preparedStatementCheckSaved.setString(2, super.requestHandler().getJSONMessage().getString("PID"));
                resultSetCheckSaved = preparedStatementCheckSaved.executeQuery();

                if(resultSetCheckSaved.next()) // I saved it, so unsave it.
                {
                    int success = removePostSaved(resultSetPost.getString("PID"));
                    if(success >= 1)
                    {
                        EsaphInternalMessageCreator esaphInternalMessageCreator = new EsaphInternalMessageCreator(MessageTypeIdentifier.CMD_USER_REMOVED_SAVED_PUBLIC,
                                resultSetPost.getLong("UID"));
                        esaphInternalMessageCreator.putInto("USNR", super.requestHandler().getThreadUID());
                        esaphInternalMessageCreator.putInto("TIME", System.currentTimeMillis());
                        esaphInternalMessageCreator.putInto("PID", resultSetPost.getString("PID"));

                        super.plServer().getExecutorSubThreads().submit(new SendInformationToUser(esaphInternalMessageCreator.getJSON(),
                                super.logUtilsRequest(),
                                super.plServer()));

                        super.requestHandler().getWriter().println("1");
                    }
                    else
                    {
                        super.requestHandler().getWriter().println("0");
                    }
                }
                else //Not contains, so save it.
                {
                    int success = insertNewSavedPost(resultSetPost.getLong("UID"), resultSetPost.getString("PID"));

                    if(success >= 1)
                    {
                        EsaphInternalMessageCreator esaphInternalMessageCreator = new EsaphInternalMessageCreator(MessageTypeIdentifier.CMD_USER_SAVED_PUBLIC,
                                resultSetPost.getLong("UID"));
                        esaphInternalMessageCreator.putInto("USNR", super.requestHandler().getThreadUID());
                        esaphInternalMessageCreator.putInto("TIME", System.currentTimeMillis());
                        esaphInternalMessageCreator.putInto("PID", resultSetPost.getString("PID"));

                        super.plServer().getExecutorSubThreads().submit(new SendInformationToUser(esaphInternalMessageCreator.getJSON(),
                                super.logUtilsRequest(),
                                super.plServer()));

                        super.requestHandler().getWriter().println("1");
                    }
                    else
                    {
                        super.requestHandler().getWriter().println("0");
                    }
                }


            }
        }
        catch (Exception ec)
        {
            super.logUtilsRequest().writeLog("PostComment() failed: " + ec);
        }
        finally {

            if(preparedStatementPost != null)
            {
                preparedStatementPost.close();
            }

            if(preparedStatementCheckSaved != null)
            {
                preparedStatementCheckSaved.close();
            }

            if(resultSetCheckSaved != null)
            {
                resultSetCheckSaved.close();
            }

            if(resultSetPost != null)
            {
                resultSetPost.close();
            }
        }
    }

    private int removePostSaved(String PID) throws Exception
    {
        PreparedStatement preparedStatementRemove = null;
        try
        {
            preparedStatementRemove = (PreparedStatement)
                    super.requestHandler().getCurrentConnectionToSql()
                            .prepareStatement(SavePublicPost.QUERY_REMOVE_SAVED);
            preparedStatementRemove.setLong(1, super.requestHandler().getThreadUID());
            preparedStatementRemove.setString(2, PID);
            return preparedStatementRemove.executeUpdate();
        }
        catch (Exception ec)
        {
            super.logUtilsRequest().writeLog("removePostSaved() failed: " + ec);
            return -1;
        }
        finally {
            if(preparedStatementRemove != null)
            {
                preparedStatementRemove.close();
            }
        }
    }

    private int insertNewSavedPost(long UID_POST_FROM, String PID) throws Exception
    {
        PreparedStatement preparedStatementInsertSaved = null;

        try
        {
            preparedStatementInsertSaved = (PreparedStatement)
                    super.requestHandler().getCurrentConnectionToSql()
                            .prepareStatement(SavePublicPost.QUERY_INSERT_NEW_SAVED);
            preparedStatementInsertSaved.setLong(1, UID_POST_FROM);
            preparedStatementInsertSaved.setLong(2, super.requestHandler().getThreadUID());
            preparedStatementInsertSaved.setString(3, PID);
            return preparedStatementInsertSaved.executeUpdate();
        }
        catch (Exception ec)
        {
            super.logUtilsRequest().writeLog("insertNewSavedPost() failed: " + ec);
            return -1;
        }
        finally
        {
            if(preparedStatementInsertSaved != null)
            {
                preparedStatementInsertSaved.close();
            }
        }
    }
}
