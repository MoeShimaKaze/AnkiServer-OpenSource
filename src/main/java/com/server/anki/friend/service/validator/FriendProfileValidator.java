package com.server.anki.friend.service.validator;

import com.server.anki.friend.entity.Friend;
import com.server.anki.friend.entity.GameSkill;
import com.server.anki.friend.entity.Talent;
import com.server.anki.friend.entity.TravelDestination;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class FriendProfileValidator {
    private static final Logger logger = LoggerFactory.getLogger(FriendProfileValidator.class);
    private static final int MAX_ITEMS = 5;

    public List<String> validate(Friend profile) {
        logger.info("开始验证用户档案: userId={}", profile.getUser() != null ? profile.getUser().getId() : "null");
        List<String> errors = new ArrayList<>();

        // 基本信息验证
        validateBasicInfo(profile, errors);

        // 位置信息验证
        validateLocation(profile, errors);

        // 学校信息验证
        validateUniversity(profile, errors);

        // 联系方式验证
        validateContact(profile, errors);

        // 列表大小验证
        validateListSizes(profile, errors);

        // 验证具体的项目内容
        validateItemContents(profile, errors);

        if (errors.isEmpty()) {
            logger.info("用户档案验证通过");
        } else {
            logger.warn("用户档案验证失败，发现 {} 个错误: {}", errors.size(), errors);
        }

        return errors;
    }

    private void validateBasicInfo(Friend profile, List<String> errors) {
        logger.debug("验证基本信息");

        if (profile.getUser() == null) {
            logger.error("用户信息为空");
            errors.add("用户信息不能为空");
        }

        if (profile.getPreferredMatchType() == null) {
            logger.error("偏好匹配类型为空");
            errors.add("请选择偏好的匹配类型");
        } else {
            logger.debug("偏好匹配类型: {}", profile.getPreferredMatchType());
        }
    }

    private void validateLocation(Friend profile, List<String> errors) {
        logger.debug("验证位置信息: latitude={}, longitude={}",
                profile.getLatitude(), profile.getLongitude());

        if (profile.getLatitude() == null || profile.getLongitude() == null) {
            logger.error("位置信息不完整");
            errors.add("位置信息不能为空");
        } else {
            if (profile.getLatitude() < -90 || profile.getLatitude() > 90) {
                logger.error("无效的纬度值: {}", profile.getLatitude());
                errors.add("纬度值无效");
            }
            if (profile.getLongitude() < -180 || profile.getLongitude() > 180) {
                logger.error("无效的经度值: {}", profile.getLongitude());
                errors.add("经度值无效");
            }
        }
    }

    private void validateUniversity(Friend profile, List<String> errors) {
        logger.debug("验证学校信息: {}", profile.getUniversity());

        if (profile.getUniversity() == null || profile.getUniversity().trim().isEmpty()) {
            logger.error("学校信息为空");
            errors.add("学校信息不能为空");
        }
    }

    private void validateContact(Friend profile, List<String> errors) {
        logger.debug("验证联系方式: type={}", profile.getContactType());

        if (profile.getContactType() == null) {
            logger.error("联系方式类型为空");
            errors.add("联系方式类型不能为空");
        }
        if (profile.getContactNumber() == null || profile.getContactNumber().trim().isEmpty()) {
            logger.error("联系方式号码为空");
            errors.add("联系方式不能为空");
        }
    }

    private void validateListSizes(Friend profile, List<String> errors) {
        logger.debug("验证列表大小限制");

        validateListSize(profile.getHobbies(), "兴趣爱好", errors);
        validateListSize(profile.getTalents(), "特长", errors);
        validateListSize(profile.getGameSkills(), "游戏技能", errors);
        validateListSize(profile.getSports(), "运动项目", errors);
        validateListSize(profile.getTravelDestinations(), "旅行目的地", errors);
        validateListSize(profile.getStudySubjects(), "学习科目", errors);
    }

    private void validateListSize(List<?> list, String fieldName, List<String> errors) {
        int size = list != null ? list.size() : 0;
        logger.debug("检查{}列表大小: {}", fieldName, size);

        if (list != null && list.size() > MAX_ITEMS) {
            logger.error("{}超出最大限制: 当前大小={}, 最大限制={}", fieldName, list.size(), MAX_ITEMS);
            errors.add(fieldName + "不能超过" + MAX_ITEMS + "个");
        }
    }

    private void validateItemContents(Friend profile, List<String> errors) {
        logger.debug("验证具体项目内容");

        // 验证游戏技能
        if (profile.getGameSkills() != null) {
            logger.debug("验证游戏技能: {} 个", profile.getGameSkills().size());
            for (GameSkill skill : profile.getGameSkills()) {
                if (skill.getGameName() == null || skill.getGameName().trim().isEmpty()) {
                    logger.error("游戏技能名称为空");
                    errors.add("游戏名称不能为空");
                }
                if (skill.getSkillLevel() == null) {
                    logger.error("游戏技能等级为空: gameName={}", skill.getGameName());
                    errors.add("游戏技能等级不能为空");
                }
            }
        }

        // 验证特长
        if (profile.getTalents() != null) {
            logger.debug("验证特长: {} 个", profile.getTalents().size());
            for (Talent talent : profile.getTalents()) {
                if (talent.getTalentName() == null || talent.getTalentName().trim().isEmpty()) {
                    logger.error("特长名称为空");
                    errors.add("特长名称不能为空");
                }
                if (talent.getProficiency() == null) {
                    logger.error("特长熟练度为空: talentName={}", talent.getTalentName());
                    errors.add("特长熟练度不能为空");
                }
            }
        }

        // 验证旅行目的地
        if (profile.getTravelDestinations() != null) {
            logger.debug("验证旅行目的地: {} 个", profile.getTravelDestinations().size());
            for (TravelDestination destination : profile.getTravelDestinations()) {
                if (destination.getDestination() == null || destination.getDestination().trim().isEmpty()) {
                    logger.error("目的地名称为空");
                    errors.add("目的地不能为空");
                }
                if (destination.getTravelType() == null) {
                    logger.error("旅行类型为空: destination={}", destination.getDestination());
                    errors.add("旅行类型不能为空");
                }
            }
        }
    }
}