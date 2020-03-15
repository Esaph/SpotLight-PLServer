/*
 *  Copyright (C) Esaph, Julian Auguscik - All Rights Reserved
 *  * Unauthorized copying of this file, via any medium is strictly prohibited
 *  * Proprietary and confidential
 *  * Written by Julian Auguscik <esaph.re@gmail.com>, March  2020
 *
 */

package Esaph;


public abstract class MessageTypeIdentifier
{
	public static final String CMD_UserSavedYourPostPrivate = "CUSYPP";
	public static final String CMD_UserUnsavedYourPostPrivate = "CUUYPP";
	public static final String CMD_UserSeenYourPostPrivate = "CUSEYPP";
    public static final String CMD_UserRemovedPostFromPrivate = "CURPFP";
    public static final String CMD_FriendStatus = "CFS";
    public static final String CMD_NewPrivatePost = "CNPP";
    public static final String CMD_UserDisallowedYouToSeeHimPostInPrivate = "CDYTPIP";
    public static final String CMD_UserAllowedYouToSeeHimPostInPrivate = "CAYTPIP";
    public static final String CMD_UserSendTextMessageInPrivate = "CUSTMIP";
    public static final String CMD_UserTyping = "CUTM";
    public static final String CMD_UserStopedTyping = "CUSTM";
    public static final String CMD_NEW_AUDIO = "CUNA";
    public static final String CMD_NEW_STICKER = "CUNS";
    public static final String CMD_NEW_EMOJIE = "CNE";
    public static final String CMD_NEW_SHARED_POST = "CNSP";
    public static final String CMD_NEW_COMMENT = "CMNPC";
    public static final String CMD_USER_REMOVED_SAVED_PUBLIC = "CUSSIP";
    public static final String CMD_USER_SAVED_PUBLIC = "CURSIP";
    public static final String CMD_NEW_SHARED_PUBLIC_POST = "CNSPP";
    public static final String CMD_YOUR_PUBLIC_POST_WAS_SHARED = "CYPPWS";
    public static final String CMD_PB_UPDATE = "CPUPU";
    public static final String CMD_PB_REMOVE = "CPRPB";
    public static final String CMD_UserDeletedAccount = "CUDA";
    public static final String CMD_UserOpenenedChat = "CUOC";
}