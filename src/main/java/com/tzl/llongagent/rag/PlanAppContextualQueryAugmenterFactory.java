package com.tzl.llongagent.rag;


import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;

/***
 * 创建上下文查询增强器的工厂
 * 在PlanAppRAGCustomAdvisor中使用
 */
public class PlanAppContextualQueryAugmenterFactory {

//    PromptTemplate emptyContextPromptTemplate = new PromptTemplate("""
//      15 -                输出下面的内容：
//      16 -                非常抱歉！EternalChristmas只能回答和旅行与规划相关的问题。
//      17 -                其他功能仍在开发中！
//      18 -                有问题可以联系唐子龙小伙伴,Q:2773604953
//      19 -                """);
//      14          return ContextualQueryAugmenter.builder()
//              21 -                .allowEmptyContext(false)
//      22 -                .emptyContextPromptTemplate(emptyContextPromptTemplate)
//      15 +                .allowEmptyContext(true)
//      16                  .build();

    public static ContextualQueryAugmenter createInstance() {
        return ContextualQueryAugmenter.builder()
                .allowEmptyContext(true)
                .build();
    }

}
