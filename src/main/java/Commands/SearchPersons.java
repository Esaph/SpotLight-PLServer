/*
 *  Copyright (C) Esaph, Julian Auguscik - All Rights Reserved
 *  * Unauthorized copying of this file, via any medium is strictly prohibited
 *  * Proprietary and confidential
 *  * Written by Julian Auguscik <esaph.re@gmail.com>, March  2020
 *
 */

package Commands;

import Esaph.LogUtilsEsaph;
import Esaph.ServerPolicy;
import PLServerMain.PostLocationServer;
import com.mysql.jdbc.PreparedStatement;
import org.json.JSONArray;
import org.json.JSONObject;
import PLServerMain.PostLocationServer.RequestHandler;

import java.sql.ResultSet;

public class SearchPersons extends EsaphCommand
{
    private static final String querySearchUser = "SELECT PB, UID, Benutzername, Vorname, Region FROM Users WHERE Benutzername LIKE ? LIMIT 15";

    public SearchPersons(PostLocationServer plServer, RequestHandler requestHandler, LogUtilsEsaph logUtilsRequest) {
        super(plServer, requestHandler, logUtilsRequest);
    }

    @Override
    public void run() throws Exception
    {
        super.requestHandler().returnConnectionToPool();
        while (super.requestHandler().getSocket().isConnected())
        {
            String inputUser = super.requestHandler().readDataCarefully(20);
            inputUser = inputUser
                    .replace("!", "!!")
                    .replace("%", "!%")
                    .replace("_", "!_")
                    .replace("[", "![");

            super.requestHandler().getConnectionToSql();

            PreparedStatement prSearchUser = null;
            ResultSet result = null;

            try
            {
                prSearchUser = (PreparedStatement) super.requestHandler().getCurrentConnectionToSql().prepareStatement(SearchPersons.querySearchUser);
                prSearchUser.setString(1,"%" + inputUser + "%");
                result = prSearchUser.executeQuery();
                JSONArray jsonArray = new JSONArray();
                while (result.next())
                {
                    JSONObject json = new JSONObject();
                    json.put("USRN", result.getString("Benutzername"));
                    json.put("UID", result.getLong("UID"));
                    json.put("VN", result.getString("Vorname"));
                    json.put("FS", ServerPolicy.getFriendshipState(super.requestHandler().getCurrentConnectionToSql(), super.requestHandler().getThreadUID(), result.getLong("UID")));
                    json.put("RE", result.getString("Region"));
                    jsonArray.put(json);
                }

                super.requestHandler().getWriter().println(jsonArray.toString());
                super.requestHandler().returnConnectionToPool();
            }
            catch (Exception ec)
            {
                super.logUtilsRequest().writeLog("Search person failed(): " + ec);
            }
            finally
            {
                if(prSearchUser != null)
                {
                    prSearchUser.close();
                }

                if(result != null)
                {
                    result.close();
                }
            }
        }
    }
}
