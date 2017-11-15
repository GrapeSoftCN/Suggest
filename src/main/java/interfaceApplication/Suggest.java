package interfaceApplication;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import JGrapeSystem.rMsg;
import Model.CommonModel;
import apps.appsProxy;
import authority.plvDef.plvType;
import interfaceModel.GrapeDBSpecField;
import interfaceModel.GrapeTreeDBModel;
import json.JSONHelper;
import nlogger.nlogger;
import security.codec;
import session.session;
import string.StringHelper;
import time.TimeHelper;

public class Suggest {
    private GrapeTreeDBModel suggest;
    private GrapeDBSpecField gDbSpecField;
    private CommonModel model;
    private session se;
    private JSONObject userInfo = null;
    private String currentWeb = null;
    private String currentUser = null;
    private Integer userType = null;

    public Suggest() {
        model = new CommonModel();
        suggest = new GrapeTreeDBModel();
        gDbSpecField = new GrapeDBSpecField();
        gDbSpecField.importDescription(appsProxy.tableConfig("Suggest"));
        suggest.descriptionModel(gDbSpecField);
        suggest.bindApp();
        suggest.enableCheck();//开启权限检查

        se = new session();
        userInfo = se.getDatas();
        if (userInfo != null && userInfo.size() != 0) {
            currentWeb = userInfo.getString("currentWeb"); // 当前站点id
            currentUser = userInfo.getMongoID("_id"); // 当前用户id
            userType =userInfo.getInt("userType");//当前用户身份
        }
    }

    /**
     * 新增咨询建议信息
     * 
     * @param info
     *            （咨询件信息数据，内容进行base64编码）
     * @return
     *
     */
    public String AddSuggest(String info) {
        String result = rMsg.netMSG(100, "提交失败");
        if (StringHelper.InvaildString(info)) {
            return rMsg.netMSG(1, "参数异常");
        }
        info = CheckParam(info);
        if (info.contains("errorcode")) {
            return info;
        }
        JSONObject object = JSONObject.toJSON(info);
        if (object != null && object.size() > 0) {
            result = add(object);
        }
        return result;
    }

    /**
     * 咨询建议回复
     * 
     * @param id
     * @param replyContent
     * @return
     */
    @SuppressWarnings("unchecked")
    public String Reply(String id, String replyContent) {
        int code = 99;
        long time = TimeHelper.nowSecond(), state = 2;
        String result = rMsg.netMSG(100, "回复失败");
        if (!StringHelper.InvaildString(replyContent)) {
            JSONObject object = JSONHelper.string2json(replyContent);
            if (object != null && object.size() > 0) {
                if (object.containsKey("replyTime")) {
                    time = Long.parseLong(object.getString("replyTime"));
                }
                object.put("replyTime", time);
                if (object.containsKey("state")) {
                    state = Long.parseLong(object.getString("state"));
                    state = state != 2 ? 2 : state;
                }
                object.put("state", state);
                code = update(id, object.toString());
            }
            result = (code == 0) ? rMsg.netMSG(0, "咨询建议件回复成功") : result;
        }
        return result;
    }

    public String PageFront(String wbid, int idx, int pageSize, String CondString) {
        JSONArray array = null;
        long total = 0;
        if (StringHelper.InvaildString(CondString)) {
            JSONArray condArray = model.buildCond(CondString);
            if (condArray != null && condArray.size() != 0) {
                suggest.where(condArray);
            } else {
                return rMsg.netPAGE(idx, pageSize, total, new JSONArray());
            }
        }
        suggest.eq("slevel", 0);
        array = suggest.dirty().field("_id,content,time,state,replyContent,replyTime").page(idx, pageSize);
        total = suggest.count();
        return rMsg.netPAGE(idx, pageSize, total,
                (array != null && array.size() > 0) ? model.decode(array) : new JSONArray());
    }

    public String Page(int idx, int pageSize) {
        return PageBy(idx, pageSize, null);
    }

    public String PageBy(int idx, int pageSize, String info) {
        long total = 0;
        JSONArray data = null;
        try {
            // 获取当前用户身份：
            // 系统管理员
            // 网站管理员
            if (StringHelper.InvaildString(currentWeb)) {
                return rMsg.netMSG(2, "当前用户信息已失效，请重新登录");
            }
            String webTree = (String) appsProxy.proxyCall("/GrapeWebInfo/WebInfo/getTree/" + currentWeb);
            if (!StringHelper.InvaildString(webTree)) {
                String[] webtree = webTree.split(",");
                suggest.or();
                for (String string : webtree) {
                    suggest.eq("wbid", string);
                }
            }
            data = suggest.dirty().desc("time").page(idx, pageSize);
            total = suggest.count();
            suggest.clear();
        } catch (Exception e) {
            nlogger.logout(e);
            data = null;
        }

        return rMsg.netPAGE(idx, pageSize, total,
                (data != null && data.size() > 0) ? model.getImg(model.decode(data)) : new JSONArray());
    }

    /**
     * 对已回复的咨询件进行评分
     * 
     * @param id
     * @param score
     * @return
     *
     */
    public String Score(String id, String score) {
        int code = 99;
        String result = rMsg.netMSG(100, "评分失败");
        // 验证咨询件是否存在
        JSONObject object = suggest.eq("_id", id).eq("state", 2).find();
        if (object == null || object.size() <= 0) {
            return rMsg.netMSG(3, "待评分咨询件不存在");
        }
        if (object != null && object.size() > 0) {
            if (!score.contains("score")) {
                score = "{\"score\":" + score + "}";
            }
            code = update(id, score);
            result = code == 0 ? rMsg.netMSG(0, "评分成功") : result;
        }
        return result;
    }

    /**
     * 设置咨询件状态，支持批量操作
     * 
     * @param id
     * @return
     *
     */
    public String setSelvel(String id, String info) {
        long code = 0;
        String[] value = null;
        String result = rMsg.netMSG(100, "咨询件设置失败");
        if (!StringHelper.InvaildString(id)) {
            value = id.split(",");
        }
        if (value != null) {
            JSONObject condString = JSONObject.toJSON(info);
            if (condString != null && condString.size() > 0) {
                try {
                    suggest.or();
                    for (String _id : value) {
                        suggest.eq("_id", _id);
                    }
                    code = suggest.data(condString).updateAll();
                } catch (Exception e) {
                    nlogger.logout(e);
                    code = 99;
                }
            }
        }
        return code > 0 ? rMsg.netMSG(0, "咨询件设置成功") : result;
    }

    /**
     * 显示某用户下的所有咨询件信息
     * 
     * @param ids
     * @param pagesize
     * @return
     */
    public String showByUser(int ids, int pagesize) {
        long total = 0;
        JSONArray array = null;
        try {
            if (StringHelper.InvaildString(currentUser)) {
                return rMsg.netMSG(3, "登录信息已失效，请重新登录");
            }
            suggest.eq("userid", currentUser);
            array = suggest.dirty().field("_id,content,time,state,replyTime,replyContent").dirty().desc("time")
                    .page(ids, pagesize);
            total = suggest.count();
            suggest.clear();
        } catch (Exception e) {
            nlogger.logout(e);
            array = null;
        }
        return rMsg.netPAGE(ids, pagesize, total,
                (array != null && array.size() > 0) ? model.decode(array) : new JSONArray());
    }

    public String FindByID(String id) {
        JSONObject object = suggest.eq("_id", id).field("_id,userid,name,consult,content,state,replyContent,score")
                .find();
        return rMsg.netMSG(0, (object != null && object.size() > 0) ? model.decode(object) : new JSONObject());
    }

    /**
     * 修改操作
     * 
     * @param id
     * @param info
     * @return
     */
    private int update(String id, String info) {
        int code = 99;
        try {
            info = CheckParam(info);
            if (info.contains("errorcode")) {
                return 99;
            }
            JSONObject object = JSONObject.toJSON(info);
            if (object != null && object.size() > 0) {
                code = suggest.eq("_id", id).data(object).update() != null ? 0 : 99;
            }
        } catch (Exception e) {
            nlogger.logout(e);
            code = 99;
        }
        return code;
    }

    /**
     * 新增操作
     * 
     * @param object
     * @return
     */
    @SuppressWarnings("unchecked")
    private String add(JSONObject object) {
        String result = rMsg.netMSG(100, "提交失败");
        // int mode = Integer.parseInt(object.get("mode").toString());
        try {
            // if (mode == 1) { // 实名
            // result = RealName(object);
            // } else {
            if (!object.containsKey("userid")) {
                object.put("userid", currentUser);
            }
            result = insert(object.toString());
            // }
            result = (result != null) ? rMsg.netMSG(0, "提交成功") : result;
        } catch (Exception e) {
            nlogger.logout(e);
            result = rMsg.netMSG(100, "提交失败");
        }
        return result;
    }

    /**
     * 插入操作
     * 
     * @param info
     * @return
     */
    private String insert(String info) {
        Object obj = null;
        JSONObject infos =JSONObject.toJSON(info);
        JSONObject rMode = new JSONObject(plvType.chkType, plvType.powerVal).puts(plvType.chkVal, 100);//设置默认查询权限
    	JSONObject uMode = new JSONObject(plvType.chkType, plvType.powerVal).puts(plvType.chkVal, 200);
    	JSONObject dMode = new JSONObject(plvType.chkType, plvType.powerVal).puts(plvType.chkVal, 300);
    	infos.put("rMode", rMode.toJSONString()); //添加默认查看权限
    	infos.put("uMode", uMode.toJSONString()); //添加默认修改权限
    	infos.put("dMode", dMode.toJSONString()); //添加默认删除权限
        try {
            obj = suggest.data(infos).insertEx();
        } catch (Exception e) {
            nlogger.logout(e);
            obj = null;
        }
        return (obj != null) ? obj.toString() : null;
    }

    /**
     * 咨询建议件编码
     * 
     * @param info
     * @return
     */
    private String CheckParam(String info) {
        String temp;
        String[] value = { "content", "replyContent" };
        String result = rMsg.netMSG(1, "参数错误");
        if (!StringHelper.InvaildString(info)) {
            JSONObject object = JSONObject.toJSON(info);
            if (object != null && object.size() != 0) {
                for (String string : value) {
                    if (object.containsKey(string)) {
                        temp = object.getString(string);
                        temp = codec.DecodeHtmlTag(temp);
                        temp = codec.decodebase64(temp);
                        object.escapeHtmlPut(string, temp);
                    }
                }
                result = object.toJSONString();
            }
        }
        return result;
    }
}
