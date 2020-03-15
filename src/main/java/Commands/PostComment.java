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

public class PostComment extends EsaphCommand
{
    private static final String QUERY_INSERT_NEW_COMMENT = "INSERT INTO CommentsPublic (UID, PID, CMT) values (?, ?, ?)";
    private static final String QUERYS_GET_PUBLIC_POST_BY_PID = "SELECT * FROM PublicPosts WHERE PID=? LIMIT 1";

    public PostComment(PostLocationServer plServer, PostLocationServer.RequestHandler requestHandler, LogUtilsEsaph logUtilsRequest) {
        super(plServer, requestHandler, logUtilsRequest);
    }

    @Override
    public void run() throws Exception
    {
        String COMMENT = super.requestHandler().getJSONMessage().getString("CMT");

        if(!isCommandValid(COMMENT))
            return;

        PreparedStatement preparedStatement = null;
        PreparedStatement preparedStatementPost = null;
        ResultSet resultSetPost = null;

        try
        {
            preparedStatementPost = (PreparedStatement)
            super.requestHandler().getCurrentConnectionToSql()
            .prepareStatement(PostComment.QUERYS_GET_PUBLIC_POST_BY_PID);
            preparedStatementPost.setString(1, super.requestHandler().getJSONMessage().getString("PID"));
            resultSetPost = preparedStatementPost.executeQuery();

            if(resultSetPost.next())
            {
                preparedStatement = (PreparedStatement) super.requestHandler().getCurrentConnectionToSql()
                        .prepareStatement(PostComment.QUERY_INSERT_NEW_COMMENT);
                preparedStatement.setLong(1, super.requestHandler().getThreadUID());
                preparedStatement.setString(2, super.requestHandler().getJSONMessage().getString("PID"));
                preparedStatement.setString(3, COMMENT);
                int success = preparedStatement.executeUpdate();

                if(success >= 1)
                {
                    EsaphInternalMessageCreator esaphInternalMessageCreator = new EsaphInternalMessageCreator(MessageTypeIdentifier.CMD_NEW_COMMENT,
                            resultSetPost.getLong("UID"));
                    esaphInternalMessageCreator.putInto("USNR", super.requestHandler().getThreadUID());
                    esaphInternalMessageCreator.putInto("TIME", System.currentTimeMillis());
                    esaphInternalMessageCreator.putInto("CMT", COMMENT);

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
        catch (Exception ec)
        {
            super.logUtilsRequest().writeLog("PostComment() failed: " + ec);
        }
        finally {

            if(preparedStatementPost != null)
            {
                preparedStatementPost.close();
            }

            if(resultSetPost != null)
            {
                resultSetPost.close();
            }

            if(preparedStatement != null)
            {
                preparedStatement.close();
            }
        }
    }

    private boolean isCommandValid(String COMMENT)
    {
        if(COMMENT != null && !COMMENT.isEmpty() && COMMENT.length() <= EsaphMaxSizes.SIZE_MAX_COMMENT_LENGTH)
        {
            return true;
        }

        return false;
    }
}
