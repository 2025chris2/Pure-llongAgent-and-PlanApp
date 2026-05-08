package com.tzl.llongagent.agent.model;

import com.tzl.llongagent.agent.BaseAgent;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

/***
 * ReAct (Reasoning and Acting) 模式的代理抽象类
 * 实现了思考 - 行动的循环模式
 */
@Slf4j
@Data
@EqualsAndHashCode(callSuper = true)
public abstract class ReActAgent extends BaseAgent {

    public abstract boolean think();

    public abstract String act();

    @Override
    public String step() {
        try{
            boolean shouldAct = think();
            if(!shouldAct) {
                return "思考完成 - 无需行动！";
            }
            return act();
        } catch(Exception e) {
            // 打印异常日志
            e.printStackTrace();
            return "ReAct 步骤执行失败" + e.getMessage();
        }

    }
}
