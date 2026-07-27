package com.example.knowyourcolleagues.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.knowyourcolleagues.entity.Transaction;
import org.apache.ibatis.annotations.Mapper;

/**
 * 交易表的数据访问接口。
 */
@Mapper
public interface TransactionMapper extends BaseMapper<Transaction> {
}
