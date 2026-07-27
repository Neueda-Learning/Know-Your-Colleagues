package com.example.knowyourcolleagues.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("alert_transactions")
public class AlertTransaction {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long alertId;
    private Long transactionId;
}
