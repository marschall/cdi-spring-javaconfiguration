package com.github.marschall.cdispringjavaconfig;

import static org.springframework.beans.factory.config.ConfigurableBeanFactory.SCOPE_PROTOTYPE;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Scope;


@Bean
@Scope(SCOPE_PROTOTYPE)
public @interface CustomStereotype {

}
