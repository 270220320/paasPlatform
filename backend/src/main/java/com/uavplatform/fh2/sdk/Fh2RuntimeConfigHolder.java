package com.uavplatform.fh2.sdk;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.uavplatform.fh2.config.Fh2Properties;
import com.uavplatform.fh2.domain.Fh2ConfigEntity;
import com.uavplatform.fh2.mapper.Fh2ConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 合并 yml 默认值与 t_fh2_config 动态配置。
 */
@Component
@RequiredArgsConstructor
public class Fh2RuntimeConfigHolder {

    private final Fh2Properties properties;
    private final Fh2ConfigMapper fh2ConfigMapper;

    public Fh2Properties effective() {
        Fh2Properties copy = new Fh2Properties();
        BeanUtils.copyProperties(properties, copy);
        try {
            Fh2ConfigEntity row = fh2ConfigMapper.selectOne(new LambdaQueryWrapper<Fh2ConfigEntity>()
                    .eq(Fh2ConfigEntity::getWorkspaceId, 1L)
                    .last("LIMIT 1"));
            if (row == null) {
                return copy;
            }
            if (row.getEnabled() != null) {
                copy.setEnabled(row.getEnabled() == 1);
            }
            overlay(copy::setFrontHost, row.getFrontHost());
            overlay(copy::setServerHost, row.getServerHost());
            overlay(copy::setGatewayBase, row.getGatewayBase());
            overlay(copy::setWssUrl, row.getWssUrl());
            overlay(copy::setUserToken, row.getUserToken());
            overlay(copy::setBindingUuid, row.getBindingUuid());
            overlay(copy::setSdkAddress, row.getSdkAddress());
            overlay(copy::setSdkPort, row.getSdkPort());
            overlay(copy::setWaylineSdkCss, row.getWaylineSdkCss());
            overlay(copy::setWaylineSdkJs, row.getWaylineSdkJs());
            overlay(copy::setDeviceSdkCss, row.getDeviceSdkCss());
            overlay(copy::setDeviceSdkJs, row.getDeviceSdkJs());
            overlay(copy::setExtraHttpHeaderKey, row.getExtraHttpHeaderKey());
            overlay(copy::setExtraHttpHeaderValue, row.getExtraHttpHeaderValue());
        } catch (Exception ignored) {
            // 表未建时回退 yml
        }
        return copy;
    }

    private void overlay(java.util.function.Consumer<String> setter, String value) {
        if (StringUtils.hasText(value)) {
            setter.accept(value.trim());
        }
    }
}
