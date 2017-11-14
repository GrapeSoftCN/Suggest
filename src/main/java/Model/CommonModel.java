package Model;

import java.util.ArrayList;
import java.util.List;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import apps.appsProxy;
import database.dbFilter;
import string.StringHelper;

public class CommonModel {
    
    /**
     * 整合参数，将JSONObject类型的参数封装成JSONArray类型
     * 
     * @param object
     * @return
     */
    public JSONArray buildCond(String Info) {
        String key;
        Object value;
        JSONArray condArray = null;
        JSONObject object = JSONObject.toJSON(Info);
        dbFilter filter = new dbFilter();
        if (object != null && object.size() > 0) {
            for (Object object2 : object.keySet()) {
                key = object2.toString();
                value = object.get(key);
                filter.eq(key, value);
            }
            condArray = filter.build();
        } else {
            condArray = JSONArray.toJSONArray(Info);
        }
        return condArray;
    }

    /**
     * 对提交的咨询内容进行解码
     * 
     * @project GrapeSuggest
     * @package interfaceApplication
     * @file Suggest.java
     * 
     * @param array
     * @return
     *
     */
    @SuppressWarnings("unchecked")
    public JSONArray decode(JSONArray array) {
        JSONObject object;
        if (array == null || array.size() <= 0) {
            return array;
        }
        int l = array.size();
        for (int i = 0; i < l; i++) {
            object = (JSONObject) array.get(i);
            array.set(i, decode(object));
        }
        return array;
    }

    @SuppressWarnings("unchecked")
    public JSONObject decode(JSONObject object) {
        String temp;
        String[] fields = { "content", "replyContent", "reviewContent" };
        if (object == null || object.size() <= 0) {
            return new JSONObject();
        }
        for (String field : fields) {
            if (object.containsKey(field)) {
                temp = object.getString(field);
                if (!StringHelper.InvaildString(temp)) {
                    object.put(field, object.escapeHtmlGet(field));
                }
            }
        }
        return object;
    }

    /**
     * 获取上传的附件的详细信息
     * 
     * @param array
     * @return
     */
    public JSONArray getImg(JSONArray array) {
        JSONObject object;
        String fileInfo = "";
        String fid = "", tempid;
        if (array == null || array.size() <= 0) {
            return new JSONArray();
        }
        for (Object obj : array) {
            object = (JSONObject) obj;
            if (object.containsKey("attr")) {
                tempid = object.getString("attr");
                if (!StringHelper.InvaildString(tempid)) {
                    fid += tempid + ",";
                }
            }
        }
        if (fid.length() > 1) {
            fid = StringHelper.fixString(fid, ',');
            if (!fid.equals("")) {
                fileInfo = (String) appsProxy.proxyCall("/GrapeFile/Files/getFiles/" + fid);
            }
        }
        return FillData(array, fileInfo);
    }

    /**
     * 填充图片url
     * @param array
     * @param fileInfo
     * @return
     */
    @SuppressWarnings("unchecked")
    private JSONArray FillData(JSONArray array, String fileInfo) {
        JSONObject object;
        if (array != null && array.size() > 0) {
            if (!StringHelper.InvaildString(fileInfo)) {
                int l = array.size();
                for (int i = 0; i < l; i++) {
                    object = (JSONObject) array.get(i);
                    array.set(i, FillData(object, fileInfo));
                }
            }
        }
        return (array != null && array.size() > 0) ? array : new JSONArray();
    }

    @SuppressWarnings("unchecked")
    private JSONObject FillData(JSONObject object, String fileInfo) {
        List<String> imgList = new ArrayList<String>();
        List<String> videoList = new ArrayList<String>();
        String attr, attrlist = "", filetype = "";
        String[] attrs = null;
        JSONObject FileInfoObj;
        if (object != null && object.size() > 0) {
            if (!StringHelper.InvaildString(fileInfo)) {
                JSONObject fileObj = JSONObject.toJSON(fileInfo);
                if (fileObj != null && fileObj.size() > 0) {
                    if (object.containsKey("attr")) {
                        attr = object.getString("attr");
                        attrs = (!StringHelper.InvaildString(attr)) ? attr.split(",") : attrs;
                    }
                    if (attrs != null) {
                        for (String string : attrs) {
                            FileInfoObj  = fileObj.getJson(string);
                            attrlist = FileInfoObj.get("filepath").toString();
                            filetype = FileInfoObj.get("filetype").toString();
                            if ("1".equals(filetype)) {
                                imgList.add(attrlist);
                            }
                            if ("2".equals(filetype)) {
                                videoList.add(attrlist);
                            }
                        }
                    }
                }
            }
            object.put("image", imgList.size() != 0 ? StringHelper.join(imgList) : "");
            object.put("video", videoList.size() != 0 ? StringHelper.join(videoList) : "");
        }
        return (object != null && object.size() > 0) ? object : new JSONObject();
    }
}
