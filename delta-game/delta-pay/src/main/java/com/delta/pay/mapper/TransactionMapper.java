package com.delta.pay.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.delta.pay.entity.Transaction;
import org.apache.ibatis.annotations.Mapper;
@Mapper
public interface TransactionMapper extends BaseMapper<Transaction> {}
