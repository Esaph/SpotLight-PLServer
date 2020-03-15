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

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SendEmojieMessage extends EsaphCommand
{
	private static final String emo_regex = "([\\u20a0-\\u32ff\\ud83c\\udc00-\\ud83d\\udeff\\udbb9\\udce5-\\udbb9\\udcee])";
	
    public SendEmojieMessage(PostLocationServer plServer, PostLocationServer.RequestHandler requestHandler, LogUtilsEsaph logUtilsRequest)
    {
        super(plServer, requestHandler, logUtilsRequest);
    }

    @Override
    public void run() throws Exception
    {
        long timeSent = System.currentTimeMillis();

        long fuid = super.requestHandler().getJSONMessage().getLong("EMPF");
        if(fuid > -1)
        {
            if(ServerPolicy.isAllowed(super.requestHandler().getCurrentConnectionToSql(),
                    super.requestHandler().getThreadUID(),
                    fuid))
            {
                String EMJ = super.requestHandler().getJSONMessage().getString("EMJ");
                if(EMJ != null && !EMJ.isEmpty())
                {
                	Matcher matcher = Pattern.compile(SendEmojieMessage.emo_regex).matcher(EMJ);
                	if (matcher.find())
                	{
                		EMJ = matcher.group();
                	}
                	else
                	{
                		return;
                	}

                	long msgHash = UUID.nameUUIDFromBytes((EMJ + timeSent).getBytes()).getMostSignificantBits();

                    EsaphInternalMessageCreator jsonMessage = new EsaphInternalMessageCreator(MessageTypeIdentifier.CMD_NEW_EMOJIE, fuid);
                    jsonMessage.putInto("USRN", super.requestHandler().getThreadUID());
                    jsonMessage.putInto("MSG", EMJ);
                    jsonMessage.putInto("MH", msgHash);
                    jsonMessage.putInto("PLINF", super.requestHandler().getJSONMessage().getJSONObject("PLINF"));
                    jsonMessage.putInto("TIME", timeSent); //Time is msghash.

                    super.plServer()
                            .getExecutorSubThreads()
                            .submit(new SendInformationToUser(jsonMessage.getJSON(), super.logUtilsRequest(), super.plServer()));

                    super.requestHandler().getWriter().println(msgHash);
                }
            }
            else
            {
                super.requestHandler().getWriter().println(-1);
            }
        }
        else
        {
            super.requestHandler().getWriter().println(-1);
        }
    }
}
