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

public class SendUserOpenedChat extends EsaphCommand
{
    public SendUserOpenedChat(PostLocationServer plServer, PostLocationServer.RequestHandler requestHandler, LogUtilsEsaph logUtilsRequest) {
        super(plServer, requestHandler, logUtilsRequest);
    }

    @Override
    public void run() throws Exception
    {
        EsaphInternalMessageCreator esaphCreator = new EsaphInternalMessageCreator(MessageTypeIdentifier.CMD_UserOpenenedChat,
                super.requestHandler().getJSONMessage().getLong("FUSRN"));

        esaphCreator.putInto("TM", System.currentTimeMillis()); //Time of message, to set only messages as read that are only older than the time.
        esaphCreator.putInto("USRN", super.requestHandler().getThreadUID());

        super.requestHandler().getWriter().println("1");

        super.plServer().getExecutorSubThreads().submit(new SendInformationToUser(esaphCreator.getJSON(), super.logUtilsRequest(), super.plServer()));
    }
}
