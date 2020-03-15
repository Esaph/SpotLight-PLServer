/*
 *  Copyright (C) Esaph, Julian Auguscik - All Rights Reserved
 *  * Unauthorized copying of this file, via any medium is strictly prohibited
 *  * Proprietary and confidential
 *  * Written by Julian Auguscik <esaph.re@gmail.com>, March  2020
 *
 */

package Commands;

import Esaph.LogUtilsEsaph;
import PLServerMain.PostLocationServer;
import PLServerMain.PostLocationServer.RequestHandler;
import com.mysql.jdbc.PreparedStatement;
import org.json.JSONArray;
import org.json.JSONObject;
import java.sql.ResultSet;

public class FetchPublicPosts extends EsaphCommand
{
	private static final String QUERY_GET_PUBLIC_POSTS = "SELECT * FROM (SELECT Users.Benutzername, Users.UID FROM Users WHERE Users.ProfilPublicity=1) AS UserTable JOIN PublicPosts ON PublicPosts.UID=UserTable.UID LIMIT 0, 20 ";

	private static final String queryGetHashtagsFromPost = "SELECT TAG_NAME FROM TAGS_PUBLIC WHERE PPID_POST=?";

	private static final String queryGetCountPublicSaved = "SELECT ( SELECT COUNT(*) FROM PublicPostsSaved WHERE PPID=?) AS COUNT_SV, (SELECT COUNT(*) FROM SharedPublic WHERE PPID=?) AS COUNT_SH, (SELECT COUNT(*) FROM CommentsPublic WHERE PPID=?) AS COUNT_CM FROM dual";

	private static final String queryLookUpISaved = "SELECT NULL FROM PublicPostsSaved WHERE UID_SAVED=? AND PPID=? LIMIT 1";

	public FetchPublicPosts(PostLocationServer plServer, RequestHandler requestHandler, LogUtilsEsaph logUtilsRequest)
	{
		super(plServer, requestHandler, logUtilsRequest);
	}

	@Override
	public void run() throws Exception
	{
		PreparedStatement preparedStatementFetchPublic = null;
		ResultSet resultFetchPublic = null;
		try
		{
			preparedStatementFetchPublic =
				(PreparedStatement) super.requestHandler().getCurrentConnectionToSql().prepareStatement(FetchPublicPosts.QUERY_GET_PUBLIC_POSTS);
			preparedStatementFetchPublic.setInt(1, super.requestHandler().getJSONMessage().getInt("ST"));
			resultFetchPublic = preparedStatementFetchPublic.executeQuery();

			JSONArray jsonArrayPublicPosts = new JSONArray();
			while(resultFetchPublic.next())
			{
				PreparedStatement prGetHashtagFromPost = (PreparedStatement) super.requestHandler().getCurrentConnectionToSql()
						.prepareStatement(FetchPublicPosts.queryGetHashtagsFromPost);
				prGetHashtagFromPost.setLong(1, resultFetchPublic.getLong("PPID"));

				ResultSet resultHashtags = prGetHashtagFromPost.executeQuery();
				JSONArray jsonArrayHashtags = new JSONArray();
				while(resultHashtags.next())
				{
					JSONObject json = new JSONObject();
					json.put("TAG", resultHashtags.getString("TAG_NAME"));
					jsonArrayHashtags.put(json);
				}
				prGetHashtagFromPost.close();
				resultHashtags.close();

				PreparedStatement preparedStatementCount = (PreparedStatement) super.requestHandler().getCurrentConnectionToSql()
						.prepareStatement(FetchPublicPosts.queryGetCountPublicSaved);
				preparedStatementCount.setLong(1, resultFetchPublic.getLong("PPID"));
				preparedStatementCount.setLong(2, resultFetchPublic.getLong("PPID"));
				preparedStatementCount.setLong(3, resultFetchPublic.getLong("PPID"));
				ResultSet resultSetCount = preparedStatementCount.executeQuery();
				int countSaved = 0;
				int countShared = 0;
				int countComments = 0;
				if(resultSetCount.next())
				{
					countSaved = resultSetCount.getInt("COUNT_SV");
					countComments = resultSetCount.getInt("COUNT_CM");
					countShared = resultSetCount.getInt("COUNT_SH");
				}
				preparedStatementCount.close();
				resultSetCount.close();

				JSONObject jsonObject = new JSONObject();
				jsonObject.put("USRN", resultFetchPublic.getString("Benutzername"));
				jsonObject.put("UID", resultFetchPublic.getLong("UID"));
				jsonObject.put("PID", resultFetchPublic.getString("PID"));
				jsonObject.put("PPID", resultFetchPublic.getLong("PPID"));
				jsonObject.put("DESC", resultFetchPublic.getString("Beschreibung"));
				jsonObject.put("TY", resultFetchPublic.getShort("TYPE"));
				jsonObject.put("TI", resultFetchPublic.getTimestamp("Time").getTime());
				jsonObject.put("CS", countSaved);
				jsonObject.put("CSH", countShared);
				jsonObject.put("CC", countComments);
				jsonObject.put("ISAV", iSavedPublicPost(resultFetchPublic.getLong("PPID")));
				jsonObject.put("ARR_EHT", jsonArrayHashtags);
				jsonArrayPublicPosts.put(jsonObject);
			}

			super.requestHandler().getWriter().println(jsonArrayPublicPosts.toString());
		}
		catch (Exception ec)
		{
			super.logUtilsRequest().writeLog("FetchPublicPosts failed(): " + ec);
		}
		finally
		{
			if(preparedStatementFetchPublic != null)
			{
				preparedStatementFetchPublic.close();
			}

			if(resultFetchPublic != null)
			{
				resultFetchPublic.close();
			}
		}
	}


	private boolean iSavedPublicPost(long PPID)
	{
		PreparedStatement preparedStatement = null;
		ResultSet resultSet = null;
		try
		{
			preparedStatement = (PreparedStatement) super.requestHandler().getCurrentConnectionToSql()
			.prepareStatement(FetchPublicPosts.queryLookUpISaved);
			preparedStatement.setLong(1, super.requestHandler().getThreadUID());
			preparedStatement.setLong(2, PPID);
			resultSet = preparedStatement.executeQuery();
			if(resultSet.next())
			{
				return true;
			}

			return false;
		}
		catch (Exception ec)
		{
			super.logUtilsRequest().writeLog("iSavedPublicPost() failed: " + ec);
			return false;
		}
		finally
		{
			try
			{
				if(preparedStatement != null)
				{
					preparedStatement.close();
				}

				if(resultSet != null)
				{
					resultSet.close();
				}
			}
			catch (Exception ec)
			{

			}
		}
	}
}
