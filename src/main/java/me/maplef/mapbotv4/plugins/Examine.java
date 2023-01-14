package me.maplef.mapbotv4.plugins;

import com.mailjet.client.ClientOptions;
import com.mailjet.client.MailjetClient;
import com.mailjet.client.MailjetRequest;
import com.mailjet.client.MailjetResponse;
import com.mailjet.client.errors.MailjetException;
import com.mailjet.client.resource.Emailv31;
import me.maplef.mapbotv4.MapbotPlugin;
import me.maplef.mapbotv4.exceptions.GroupNotAllowedException;
import me.maplef.mapbotv4.exceptions.InvalidSyntaxException;
import me.maplef.mapbotv4.exceptions.PlayerNotFoundException;
import me.maplef.mapbotv4.managers.ConfigManager;
import me.maplef.mapbotv4.utils.DatabaseOperator;
import net.mamoe.mirai.message.data.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

import static com.mailjet.client.resource.Emailv31.Message.TEMPLATEERROR_REPORTING;

public class Examine implements MapbotPlugin {
    ConfigManager configManager = new ConfigManager();
    FileConfiguration config = configManager.getConfig();
    FileConfiguration messages = configManager.getMessageConfig();

    private final Long opGroup = config.getLong("op-group");
    private final Long examineGroup = config.getLong("examine-group");
    private final String apiKey = config.getString("examine.api-key");
    private final String apiSecretKey = config.getString("examine.api-secret-key");
    private final int approvedTemplateId = config.getInt("examine.approved-template-id");
    private final int unapprovedTemplateId = config.getInt("examine.unapproved-template-id");

    public String approved(long QQ, String mail) {
        try {
            if ((boolean) DatabaseOperator.queryExamine(QQ).get("APPROVED")) return "该玩家已经通过审核了!";
        } catch (PlayerNotFoundException ignored) {
        } catch (Exception e) {
            e.printStackTrace();
        }
        String code = generateInvitationCode();
        try {
            String order = String.format("INSERT INTO EXAMINE (QQ, MAIL, CODE, USED, APPROVED) VALUES ('%s', '%s', '%s', 0, 1);", QQ, mail, code);
            new DatabaseOperator().executeCommand(order);
            sendApprovedMail(mail, String.valueOf(QQ), code);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "执行成功";
    }

    public String unApproved(long QQ, String mail, String reason) {
        try {
            DatabaseOperator.queryExamine(QQ);
            return "该玩家已经通过审核了!";
        } catch (PlayerNotFoundException ignored) {
        } catch (Exception e) {
            e.printStackTrace();
        }
        String code = generateInvitationCode();
        try {
            String order = String.format("INSERT INTO EXAMINE (QQ, MAIL, CODE, USED, APPROVED) VALUES ('%s', '%s', '%s', 0, 0);", QQ, mail, "null");
            new DatabaseOperator().executeCommand(order);
            sendUnapprovedMail(mail, String.valueOf(QQ), reason);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "执行成功";
    }

    public String generateInvitationCode() {
        String str = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        Random random = new Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            int number = random.nextInt(36);
            sb.append(str.charAt(number));
        }
        return sb.toString();
    }

    @Override
    public MessageChain onEnable(@NotNull Long groupID, @NotNull Long senderID, Message[] args, @Nullable QuoteReply quoteReply) throws Exception {
        if (!Objects.equals(groupID, examineGroup))
            throw new GroupNotAllowedException();
        if (args.length < 3) throw new InvalidSyntaxException();

        try {
            String order = "CREATE TABLE IF NOT EXISTS EXAMINE (" +
                    "    QQ      TEXT    NOT NULL," +
                    "    MAIL    TEXT    NOT NULL," +
                    "    CODE    TEXT    NOT NULL," +
                    "    USED    BOOLEAN DEFAULT 0," +
                    "    APPROVED BOOLEAN DEFAULT 0" +
                    ");";
            new DatabaseOperator().executeCommand(order);
        } catch (Exception e) {
            e.printStackTrace();
        }

        long QQ;
        try {
            QQ = Long.parseLong(args[0].contentToString());
        } catch (Exception e) {
            return MessageUtils.newChain(new At(senderID)).plus(" 请输入正确的QQ号");
        }
        String mail;
        StringBuilder sb = new StringBuilder();
        try {
            mail = args[1].contentToString();
        } catch (Exception e) {
            return MessageUtils.newChain(new At(senderID)).plus(" 请输入正确的邮箱");
        }
        if (args[2].contentToString().equals("通过"))
            return MessageUtils.newChain(new At(senderID)).plus(" " + approved(QQ, mail));
        else if (args[2].contentToString().equals("不通过")) {
            if (args.length > 3) {
                for (int i = 3; i < args.length; i++) {
                    sb.append(args[i].contentToString()).append(" ");
                }
                sb.deleteCharAt(sb.length() - 1);
                String reason = sb.toString();
                return MessageUtils.newChain(new At(senderID)).plus(" " + unApproved(QQ, mail, reason));
            } else return MessageUtils.newChain(new At(senderID)).plus(" 请填入不通过的原因");
        } else MessageUtils.newChain(new At(senderID)).plus(" 参数第三项请输入通过或不通过");
        return MessageUtils.newChain(new At(senderID)).plus(" 指令输入错误");
    }

    @Override
    public Map<String, Object> register() throws NoSuchMethodException {
        Map<String, Object> info = new HashMap<>();
        Map<String, Method> commands = new HashMap<>();
        Map<String, String> usages = new HashMap<>();

        commands.put("审核", Examine.class.getMethod("onEnable", Long.class, Long.class, Message[].class, QuoteReply.class));

        usages.put("审核", "#审核 <QQ> <邮箱> <通过|不通过> [不通过的原因] - 审核新玩家");

        info.put("name", "审核");
        info.put("commands", commands);
        info.put("usages", usages);
        info.put("author", "LQSnow");
        info.put("description", "审核新玩家");
        info.put("version", "1.0");

        return info;
    }

    public void sendApprovedMail(String mail, String QQ, String code) throws MailjetException {
        MailjetClient client;
        MailjetRequest request;
        MailjetResponse response;
        client = new MailjetClient(ClientOptions.builder().apiKey(apiKey).apiSecretKey(apiSecretKey).build());
        request = new MailjetRequest(Emailv31.resource)
                .property(Emailv31.MESSAGES, new JSONArray()
                        .put(new JSONObject()
                                .put(Emailv31.Message.FROM, new JSONObject()
                                        .put("Email", "admin@catland.top")
                                        .put("Name", "猫猫大陆"))
                                .put(Emailv31.Message.TO, new JSONArray()
                                        .put(new JSONObject()
                                                .put("Email", mail)
                                                .put("Name", QQ)))
                                .put(Emailv31.Message.TEMPLATEID, approvedTemplateId)
                                .put(Emailv31.Message.TEMPLATELANGUAGE, true)
                                .put(Emailv31.Message.SUBJECT, "猫猫大陆白名单审核结果通知")
                                .put(Emailv31.Message.VARIABLES, new JSONObject()
                                        .put("[invite_code]", code)
                                        .put("[email_to]", mail)
                                .put(TEMPLATEERROR_REPORTING, "lq_snow@outlook.com"))));
        response = client.post(request);
        System.out.println(response.getStatus());
        System.out.println(response.getData());
    }

    public void sendUnapprovedMail(String mail, String QQ, String reason) throws MailjetException {
        MailjetClient client;
        MailjetRequest request;
        MailjetResponse response;
        client = new MailjetClient(ClientOptions.builder().apiKey(apiKey).apiSecretKey(apiSecretKey).build());
        request = new MailjetRequest(Emailv31.resource)
                .property(Emailv31.MESSAGES, new JSONArray()
                        .put(new JSONObject()
                                .put(Emailv31.Message.FROM, new JSONObject()
                                        .put("Email", "admin@catland.top")
                                        .put("Name", "猫猫大陆"))
                                .put(Emailv31.Message.TO, new JSONArray()
                                        .put(new JSONObject()
                                                .put("Email", mail)
                                                .put("Name", QQ)))
                                .put(Emailv31.Message.TEMPLATEID, unapprovedTemplateId)
                                .put(Emailv31.Message.TEMPLATELANGUAGE, true)
                                .put(Emailv31.Message.SUBJECT, "猫猫大陆白名单审核结果通知")
                                .put(Emailv31.Message.VARIABLES, new JSONObject()
                                        .put("[notpass_reason]", reason)
                                        .put("[email_to]", mail))));
        response = client.post(request);
        System.out.println(response.getStatus());
        System.out.println(response.getData());
    }
}
