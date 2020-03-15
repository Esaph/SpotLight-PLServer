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

public class SendTextMessage extends EsaphCommand
{
    public SendTextMessage(PostLocationServer plServer, PostLocationServer.RequestHandler requestHandler, LogUtilsEsaph logUtilsRequest)
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
                EsaphInternalMessageCreator jsonMessage = new EsaphInternalMessageCreator(MessageTypeIdentifier.CMD_UserSendTextMessageInPrivate,
                        fuid);
                jsonMessage.putInto("USRN", super.requestHandler().getThreadUID());
                jsonMessage.putInto("MSG", super.requestHandler().getJSONMessage().getString("MSG"));
                jsonMessage.putInto("PLINF", super.requestHandler().getJSONMessage().getJSONObject("PLINF"));
                jsonMessage.putInto("TIME", timeSent);
                jsonMessage.putInto("MH",
                        UUID.nameUUIDFromBytes((super.requestHandler().getJSONMessage().getString("MSG") + timeSent)
                                        .getBytes())
                                .getMostSignificantBits());

                super.plServer().getExecutorSubThreads().submit(new SendInformationToUser(jsonMessage.getJSON(), super.logUtilsRequest(), super.plServer()));
                super.requestHandler().getWriter().println(timeSent);
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
