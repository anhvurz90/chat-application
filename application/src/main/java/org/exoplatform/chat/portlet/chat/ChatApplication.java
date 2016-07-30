/*
 * Copyright (C) 2012 eXo Platform SAS.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.exoplatform.chat.portlet.chat;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.portlet.PortletPreferences;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.fileupload.FileItem;
import org.exoplatform.chat.bean.File;
import org.exoplatform.chat.common.utils.ChatUtils;
import org.exoplatform.chat.listener.ServerBootstrap;
import org.exoplatform.chat.model.SpaceBean;
import org.exoplatform.chat.model.SpaceBeans;
import org.exoplatform.chat.services.ChatService;
import org.exoplatform.chat.services.UserService;
import org.exoplatform.chat.utils.PropertyManager;
import org.exoplatform.commons.api.ui.ActionContext;
import org.exoplatform.commons.api.ui.PlugableUIService;
import org.exoplatform.commons.api.ui.RenderContext;
import org.exoplatform.commons.utils.ListAccess;
import org.exoplatform.services.organization.OrganizationService;
import org.exoplatform.services.organization.User;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import juzu.Path;
import juzu.Resource;
import juzu.Response;
import juzu.SessionScoped;
import juzu.View;
import juzu.impl.common.Tools;
import juzu.plugin.ajax.Ajax;
import juzu.request.SecurityContext;
import juzu.template.Template;

@SessionScoped
public class ChatApplication
{

  @Inject
  @Path("index.gtmpl")
  Template index;


  String token_ = "---";
  String remoteUser_ = null;
  String fullname_ = null;
  boolean isAdmin_=false;
  Boolean isTeamAdmin_ = null;

  boolean profileInitialized_ = false;

  private static final Logger LOG = Logger.getLogger("ChatApplication");

  OrganizationService organizationService_;

  SpaceService spaceService_;
  
  PlugableUIService uiService;

  String dbName;

  @Inject
  Provider<PortletPreferences> providerPreferences;

  @Inject
  DocumentsData documentsData_;

  @Inject
  CalendarService calendarService_;

  @Inject
  WikiService wikiService_;
  
  @Inject
  ResourceBundle bundle;
  
  public static final String TASK_PLUGIN = "task";

  @Inject
  public ChatApplication(OrganizationService organizationService, SpaceService spaceService, PlugableUIService uiService)
  {
    this.uiService = uiService;
    organizationService_ = organizationService;
    spaceService_ = spaceService;
    dbName = ChatUtils.getDBName();
  }

  @View
  public Response.Content index(SecurityContext securityContext)
  {
    remoteUser_ = securityContext.getRemoteUser();
    boolean isPublic = (remoteUser_==null);
    if (isPublic) remoteUser_ = UserService.ANONIM_USER;
    String chatServerURL = PropertyManager.getProperty(PropertyManager.PROPERTY_CHAT_SERVER_URL);
    String chatIntervalChat = PropertyManager.getProperty(PropertyManager.PROPERTY_INTERVAL_CHAT);
    String chatIntervalSession = PropertyManager.getProperty(PropertyManager.PROPERTY_INTERVAL_SESSION);
    String chatIntervalStatus = PropertyManager.getProperty(PropertyManager.PROPERTY_INTERVAL_STATUS);
    String chatIntervalUsers = PropertyManager.getProperty(PropertyManager.PROPERTY_INTERVAL_USERS);
    String publicModeEnabled = PropertyManager.getProperty(PropertyManager.PROPERTY_PUBLIC_MODE);
    String servicesImplementation = PropertyManager.getProperty(PropertyManager.PROPERTY_SERVICES_IMPLEMENTATION);
    String dbServerMode = PropertyManager.getProperty(PropertyManager.PROPERTY_SERVER_TYPE);
    String demoMode = (PropertyManager.PROPERTY_SERVER_TYPE_EMBED.equals(dbServerMode) || PropertyManager.PROPERTY_SERVICE_IMPL_JCR.equals(servicesImplementation))?"DEV":"PROD";
    String plfUserStatusUpdateUrl = PropertyManager.getProperty(PropertyManager.PROPERTY_PLF_USER_STATUS_UPDATE_URL);

    String fullname = (fullname_==null || fullname_.isEmpty()) ? remoteUser_ : fullname_;

    PortletPreferences portletPreferences = providerPreferences.get();
    String view = portletPreferences.getValue("view", "responsive");
    if (!"normal".equals(view) && !"responsive".equals(view) && !"public".equals(view))
      view = "responsive";

    String fullscreen = portletPreferences.getValue("fullscreen", "false");

    DateFormat df = new SimpleDateFormat("MM/dd/yyyy");
    Date today = Calendar.getInstance().getTime();
    String todayDate = df.format(today);

    RenderContext taskItemCtx = new RenderContext(TASK_PLUGIN);
    taskItemCtx.setRsBundle(bundle);    
    org.exoplatform.commons.api.ui.Response taskItemRes = this.uiService.render(taskItemCtx);    
    
    String taskItem = "";
    String taskPopup = "";    
    
    RenderContext taskPopupCtx = new RenderContext(TASK_PLUGIN);
    taskPopupCtx.getParams().put("renderPopup", true);
    taskPopupCtx.getParams().put("today", todayDate);
    taskPopupCtx.setActionUrl(ChatApplication_.createTask(null, null, null, null, null).toString());
    taskPopupCtx.setRsBundle(bundle);
    org.exoplatform.commons.api.ui.Response taskPopupRes = this.uiService.render(taskPopupCtx); 
    
    try {
      if (taskItemRes != null) {
        taskItem = new String(taskItemRes.getData(), "UTF-8");
      }
      if (taskPopupRes != null) {
        taskPopup = new String(taskPopupRes.getData(), "UTF-8");
      }      
    } catch (Exception ex) {
      LOG.log(Level.SEVERE, ex.getMessage(), ex);
    }

    return index.with().set("user", remoteUser_).set("room", "noroom")
            .set("token", token_).set("chatServerURL", chatServerURL)
            .set("fullname", fullname)
            .set("chatIntervalChat", chatIntervalChat).set("chatIntervalSession", chatIntervalSession)
            .set("chatIntervalStatus", chatIntervalStatus).set("chatIntervalUsers", chatIntervalUsers)
            .set("plfUserStatusUpdateUrl", plfUserStatusUpdateUrl)
            .set("publicMode", isPublic)
            .set("publicModeEnabled", publicModeEnabled)
            .set("view", view)
            .set("fullscreen", fullscreen)
            .set("demoMode", demoMode)
            .set("today", todayDate)
            .set("dbName", dbName)
            .set("taskPopup", taskPopup)
            .set("taskMenuItem", taskItem)
            .ok()
            .withMetaTag("viewport", "width=device-width, initial-scale=1.0")
            .withAssets("chat-" + view)
            .withCharset(Tools.UTF_8);

  }

  @Ajax
  @Resource
  public Response.Content maintainSession()
  {
    return Response.ok("OK").withMimeType("text/html; charset=UTF-8").withHeader("Cache-Control", "no-cache");
  }

  @Ajax
  @Resource
  public Response.Content initChatProfile() {
    // Update new fullName;
    if (!UserService.ANONIM_USER.equals(remoteUser_)) {
      fullname_ = ServerBootstrap.getUserFullName(remoteUser_, dbName);
    }

    String out = "{\"token\": \""+token_+"\", \"fullname\": \""+fullname_+"\", \"msg\": \"nothing to update\", \"isAdmin\": \""+isAdmin_+"\", \"isTeamAdmin\": \""+isTeamAdmin_+"\"}";
    if (!profileInitialized_ && !UserService.ANONIM_USER.equals(remoteUser_))
    {
      try
      {
        // Generate and store token if doesn't exist yet.
        token_ = ServerBootstrap.getToken(remoteUser_);

        // Add User in the DB
        addUser(remoteUser_, token_, dbName);

        // Set user's Full Name in the DB
        saveFullNameAndEmail(remoteUser_, dbName);

        if ("true".equals(PropertyManager.getProperty(PropertyManager.PROPERTY_PUBLIC_MODE)))
        {
          Collection ms = organizationService_.getMembershipHandler().findMembershipsByUserAndGroup(remoteUser_, PropertyManager.getProperty(PropertyManager.PROPERTY_PUBLIC_ADMIN_GROUP));
          isAdmin_= (ms!=null && ms.size()>0);
        }

        if (isTeamAdmin_==null)
        {
          Collection ms = organizationService_.getMembershipHandler().findMembershipsByUserAndGroup(remoteUser_, PropertyManager.getProperty(PropertyManager.PROPERTY_TEAM_ADMIN_GROUP));
          isTeamAdmin_ = (ms!=null && ms.size()>0);
        }

        if (!UserService.ANONIM_USER.equals(remoteUser_))
        {
          ServerBootstrap.setAsAdmin(remoteUser_, isAdmin_, dbName);
        }

        out = "{\"token\": \""+token_+"\", \"fullname\": \""+fullname_+"\", \"msg\": \"updated\", \"isAdmin\": \""+isAdmin_+"\", \"isTeamAdmin\": \""+isTeamAdmin_+"\"}";
        profileInitialized_ = true;
      }
      catch (Exception e)
      {
        LOG.warning(e.getMessage());
        profileInitialized_ = false;
        return Response.notFound("Error during init, try later");
      }
    }
    if (!UserService.ANONIM_USER.equals(remoteUser_))
    {
      // Set user's Spaces in the DB
      saveSpaces(remoteUser_, dbName);
    }

    return Response.ok(out).withMimeType("text/event-stream; charset=UTF-8").withHeader("Cache-Control", "no-cache")
    		       .withCharset(Tools.UTF_8);

  }

  @Resource
  @Ajax
  public Response.Content upload(String room, String targetUser, String targetFullname, String encodedFileName, FileItem userfile, SecurityContext securityContext) {
    try {
      targetFullname = URLDecoder.decode(targetFullname, "UTF-8");
    } catch (UnsupportedEncodingException e) {
      // Cannot do anything here
    }
    LOG.info("File is uploaded in " + room + " (" + targetFullname + ")");
    if (userfile.isFormField())
    {
      String fieldName = userfile.getFieldName();
      if ("room".equals(fieldName))
      {
        room = userfile.getString();
        LOG.info("room : " + room);
      }
    }
    if (userfile.getFieldName().equals("userfile"))
    {

      String uuid = null;
      if (targetUser.startsWith(ChatService.SPACE_PREFIX))
      {
        uuid = documentsData_.storeFile(userfile, encodedFileName, targetFullname, false);
      }
      else
      {
        remoteUser_ = securityContext.getRemoteUser();
        uuid = documentsData_.storeFile(userfile, encodedFileName, remoteUser_, true);
        documentsData_.setPermission(uuid, targetUser);
      }
      File file = documentsData_.getNode(uuid);

      LOG.info(file.toJSON());


      return Response.ok(file.toJSON())
              .withMimeType("application/json; charset=UTF-8").withHeader("Cache-Control", "no-cache").withCharset(Tools.UTF_8);
    }


    return Response.ok("{\"status\":\"File has not been uploaded !\"}")
            .withMimeType("application/json; charset=UTF-8").withHeader("Cache-Control", "no-cache");
  }

  @Ajax
  @Resource
  public Response.Content createTask(String username, String dueDate, String task, String roomName, String isSpace) {    
//    try {
//      calendarService_.saveTask(remoteUser_, username, task, roomName, isSpace, today, sdf.parse(dueDate+" 23:59"));
//    } catch (ParseException e) {
//      LOG.warning("parse exception during task creation");
//      return Response.notFound("Error during task creation");
//    } catch (Exception e) {
//      LOG.warning("exception during task creation");
//      return Response.notFound("Error during task creation");
//    }
//
//    
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("username", username);
    params.put("dueDate", dueDate);
    params.put("task", task);
    params.put("roomName", roomName);
    params.put("isSpace", isSpace);
    //
    ActionContext actContext = new ActionContext(TASK_PLUGIN);
    actContext.setParams(params);
    uiService.processAction(actContext);
    
    return Response.ok("{\"status\":\"ok\"}")
        .withMimeType("application/json; charset=UTF-8").withHeader("Cache-Control", "no-cache");
  }

  @Ajax
  @Resource
  public Response.Content createEvent(String space, String users, String summary, String startDate, String startTime, String endDate, String endTime, String location) {
    SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy hh:mm a");
    try {
      calendarService_.saveEvent(remoteUser_, space, users, summary, sdf.parse(startDate + " " + startTime),
              sdf.parse(endDate + " " + endTime), location);

    } catch (ParseException e) {
      LOG.warning("parse exception during event creation");
      return Response.notFound("Error during event creation");
    } catch (Exception e) {
      LOG.warning("exception during event creation");
      return Response.notFound("Error during event creation");
    }


    return Response.ok("{\"status\":\"ok\"}")
            .withMimeType("application/json; charset=UTF-8").withHeader("Cache-Control", "no-cache");

  }

  @Ajax
  @Resource
  public Response.Content saveWiki(String targetFullname, String content) {

    SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy HH-mm");
    String group = null, title = null, path="";
    JSONObject jsonObject = (JSONObject)JSONValue.parse(content);
    String typeRoom = (String)jsonObject.get("typeRoom");
    String xwiki = (String)jsonObject.get("xwiki");
    xwiki = xwiki.replaceAll("~", "~~");
    xwiki = xwiki.replaceAll("&#38", "&");
    xwiki = xwiki.replaceAll("&lt;", "<");
    xwiki = xwiki.replaceAll("&gt;", ">");
    xwiki = xwiki.replaceAll("&quot;","\"");
    xwiki = xwiki.replaceAll("<br/>","\n");
    xwiki = xwiki.replaceAll("&#92","\\\\");
    xwiki = xwiki.replaceAll("  ","\t");
    ArrayList<String> users = (ArrayList<String>) jsonObject.get("users");
    if (ChatService.TYPE_ROOM_SPACE.equalsIgnoreCase(typeRoom)) {
      Space spaceBean = spaceService_.getSpaceByDisplayName(targetFullname);
      if (spaceBean!=null) // Space use case
      {
        group = spaceBean.getGroupId();
        if (group.startsWith("/")) group = group.substring(1);
        title = "Meeting "+sdf.format(new Date());
        path = wikiService_.createSpacePage(remoteUser_, title, xwiki, group, users);
      }
    }
    else // Team use case & one to one use case
    {
      title = targetFullname+" Meeting "+sdf.format(new Date());
      path = wikiService_.createIntranetPage(remoteUser_, title, xwiki, users);
    }

    return Response.ok("{\"status\":\"ok\", \"path\":\""+path+"\"}")
            .withMimeType("application/json; charset=UTF-8").withHeader("Cache-Control", "no-cache").withCharset(Tools.UTF_8);

  }

  public Response.Content createDemoUser(String fullname, String email, String isPublic, String dbName)
  {
    String out = "created";
    boolean isPublicUser = "true".equals(isPublic);

    String username = UserService.ANONIM_USER + fullname.trim().toLowerCase().replace(" ", "-").replace(".", "-");
    remoteUser_ = username;
    token_ = ServerBootstrap.getToken(remoteUser_);
    addUser(remoteUser_, token_, dbName);
    ServerBootstrap.addUserFullNameAndEmail(username, fullname, email, dbName);
    ServerBootstrap.setAsAdmin(username, false ,dbName);
    if (!isPublicUser) saveDemoSpace(username, dbName);

    StringBuffer json = new StringBuffer();
    json.append("{ \"username\": \"").append(remoteUser_).append("\"");
    json.append(", \"token\": \"").append(token_).append("\" }");

    return Response.ok(json).withMimeType("text/html; charset=UTF-8").withHeader("Cache-Control", "no-cache")
                   .withCharset(Tools.UTF_8);
  }

  protected void addUser(String remoteUser, String token, String dbName)
  {
    ServerBootstrap.addUser(remoteUser, token, dbName);
  }

  protected String saveFullNameAndEmail(String username, String dbName)
  {
    String fullname = username;
    try
    {

      fullname = ServerBootstrap.getUserFullName(username, dbName);
      if (fullname==null || fullname.isEmpty())
      {
        User user = organizationService_.getUserHandler().findUserByName(username);
        if (user!=null)
        {
          fullname = user.getFirstName()+" "+user.getLastName();
          ServerBootstrap.addUserFullNameAndEmail(username, fullname, user.getEmail(), dbName);
        }
      }
    }
    catch (Exception e)
    {
      LOG.warning(e.getMessage());
    }
    return fullname;
  }

  protected void setAsAdmin(String username, boolean isAdmin, String dbName)
  {
    try
    {

      ServerBootstrap.setAsAdmin(username, isAdmin, dbName);

    }
    catch (Exception e)
    {
      LOG.warning(e.getMessage());
    }
  }

  protected void saveSpaces(String username, String dbName)
  {
    try
    {
      ListAccess<Space> spacesListAccess = spaceService_.getAccessibleSpacesWithListAccess(username);
      List<Space> spaces = Arrays.asList(spacesListAccess.load(0, spacesListAccess.getSize()));
      ArrayList<SpaceBean> beans = new ArrayList<SpaceBean>();
      for (Space space:spaces)
      {
        SpaceBean spaceBean = new SpaceBean();
        spaceBean.setDisplayName(space.getDisplayName());
        spaceBean.setGroupId(space.getGroupId());
        spaceBean.setId(space.getId());
        spaceBean.setShortName(space.getShortName());
        beans.add(spaceBean);
      }
      ServerBootstrap.setSpaces(username, new SpaceBeans(beans), dbName);
    }
    catch (Exception e)
    {
      LOG.warning(e.getMessage());
    }
  }

  protected void saveDemoSpace(String username, String dbName)
  {
    try
    {
      ArrayList<SpaceBean> beans = new ArrayList<SpaceBean>();
      SpaceBean spaceBean = new SpaceBean();
      spaceBean.setDisplayName("Welcome Space");
      spaceBean.setGroupId("/public");
      spaceBean.setId("welcome_space");
      spaceBean.setShortName("welcome_space");
      beans.add(spaceBean);

      ServerBootstrap.setSpaces(username, new SpaceBeans(beans), dbName);
    }
    catch (Exception e)
    {
      LOG.warning(e.getMessage());
    }
  }
}
