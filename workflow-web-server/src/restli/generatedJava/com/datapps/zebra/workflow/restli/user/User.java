
package com.datapps.zebra.workflow.restli.user;

import java.util.List;
import javax.annotation.Generated;
import com.linkedin.data.DataMap;
import com.linkedin.data.schema.PathSpec;
import com.linkedin.data.schema.RecordDataSchema;
import com.linkedin.data.template.DataTemplateUtil;
import com.linkedin.data.template.GetMode;
import com.linkedin.data.template.RecordTemplate;
import com.linkedin.data.template.SetMode;


/**
 * Azkaban User restli info
 * 
 */
@Generated(value = "com.linkedin.pegasus.generator.PegasusDataTemplateGenerator", comments = "LinkedIn Data Template. Generated from /Users/user/work/datapps/projects/azkaban/azkaban-web-server/src/restli/schemas/azkaban/restli/user/User.pdsc.", date = "Tue Aug 21 12:31:18 CST 2018")
public class User
    extends RecordTemplate
{

    private final static User.Fields _fields = new User.Fields();
    private final static RecordDataSchema SCHEMA = ((RecordDataSchema) DataTemplateUtil.parseSchema("{\"type\":\"record\",\"name\":\"User\",\"namespace\":\"azkaban.restli.user\",\"doc\":\"Azkaban User restli info\",\"fields\":[{\"name\":\"userId\",\"type\":\"string\",\"doc\":\"The username this session\"},{\"name\":\"email\",\"type\":\"string\",\"doc\":\"User email\"}]}"));
    private final static RecordDataSchema.Field FIELD_UserId = SCHEMA.getField("userId");
    private final static RecordDataSchema.Field FIELD_Email = SCHEMA.getField("email");

    public User() {
        super(new DataMap(), SCHEMA);
    }

    public User(DataMap data) {
        super(data, SCHEMA);
    }

    public static User.Fields fields() {
        return _fields;
    }

    /**
     * Existence checker for userId
     * 
     * @see Fields#userId
     */
    public boolean hasUserId() {
        return contains(FIELD_UserId);
    }

    /**
     * Remover for userId
     * 
     * @see Fields#userId
     */
    public void removeUserId() {
        remove(FIELD_UserId);
    }

    /**
     * Getter for userId
     * 
     * @see Fields#userId
     */
    public String getUserId(GetMode mode) {
        return obtainDirect(FIELD_UserId, String.class, mode);
    }

    /**
     * Getter for userId
     * 
     * @see Fields#userId
     */
    public String getUserId() {
        return getUserId(GetMode.STRICT);
    }

    /**
     * Setter for userId
     * 
     * @see Fields#userId
     */
    public User setUserId(String value, SetMode mode) {
        putDirect(FIELD_UserId, String.class, String.class, value, mode);
        return this;
    }

    /**
     * Setter for userId
     * 
     * @see Fields#userId
     */
    public User setUserId(String value) {
        putDirect(FIELD_UserId, String.class, String.class, value, SetMode.DISALLOW_NULL);
        return this;
    }

    /**
     * Existence checker for email
     * 
     * @see Fields#email
     */
    public boolean hasEmail() {
        return contains(FIELD_Email);
    }

    /**
     * Remover for email
     * 
     * @see Fields#email
     */
    public void removeEmail() {
        remove(FIELD_Email);
    }

    /**
     * Getter for email
     * 
     * @see Fields#email
     */
    public String getEmail(GetMode mode) {
        return obtainDirect(FIELD_Email, String.class, mode);
    }

    /**
     * Getter for email
     * 
     * @see Fields#email
     */
    public String getEmail() {
        return getEmail(GetMode.STRICT);
    }

    /**
     * Setter for email
     * 
     * @see Fields#email
     */
    public User setEmail(String value, SetMode mode) {
        putDirect(FIELD_Email, String.class, String.class, value, mode);
        return this;
    }

    /**
     * Setter for email
     * 
     * @see Fields#email
     */
    public User setEmail(String value) {
        putDirect(FIELD_Email, String.class, String.class, value, SetMode.DISALLOW_NULL);
        return this;
    }

    @Override
    public User clone()
        throws CloneNotSupportedException
    {
        return ((User) super.clone());
    }

    @Override
    public User copy()
        throws CloneNotSupportedException
    {
        return ((User) super.copy());
    }

    public static class Fields
        extends PathSpec
    {


        public Fields(List<String> path, String name) {
            super(path, name);
        }

        public Fields() {
            super();
        }

        /**
         * The username this session
         * 
         */
        public PathSpec userId() {
            return new PathSpec(getPathComponents(), "userId");
        }

        /**
         * User email
         * 
         */
        public PathSpec email() {
            return new PathSpec(getPathComponents(), "email");
        }

    }

}
