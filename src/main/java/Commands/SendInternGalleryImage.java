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

import java.util.UUID;

public class SendInternGalleryImage extends EsaphCommand
{
    public SendInternGalleryImage(PostLocationServer plServer, PostLocationServer.RequestHandler requestHandler, LogUtilsEsaph logUtilsRequest) {
        super(plServer, requestHandler, logUtilsRequest);
    }

    @Override
    public void run() throws Exception
    {
        long timeSent = System.currentTimeMillis();
        long msgHash = UUID.nameUUIDFromBytes((""+timeSent).getBytes()).getMostSignificantBits();
    }
}
